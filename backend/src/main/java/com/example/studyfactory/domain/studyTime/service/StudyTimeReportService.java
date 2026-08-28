package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.model.StudyPresenceIntervalRow;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import com.example.studyfactory.domain.studyTime.dto.StudyTimeDailyReportResponse;
import com.example.studyfactory.domain.studyTime.dto.StudyTimeReportResponse;
import com.example.studyfactory.domain.studyTime.dto.StudyTimeReportTotalsResponse;
import com.example.studyfactory.domain.studyTime.exception.StudyTimeException;
import com.example.studyfactory.domain.studyTime.model.BreakStudyInterval;
import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.model.StudyTimeCalculationInput;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyTimeReportService {

    private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");
    private static final long MAX_REPORT_INCLUSIVE_DAYS = 366L;

    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final StudyBreakSessionRepository studyBreakSessionRepository;
    private final MemberRepository memberRepository;
    private final StudyTimeCalculator studyTimeCalculator;
    private final StudyTimeLeaveExclusionService leaveExclusionService;
    private final StudyPresenceAutoClosePolicy autoClosePolicy;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudyTimeReportResponse findMine(Long memberId, LocalDate fromDate, LocalDate toDate) {
        Instant asOf = clock.instant();
        validateDateRange(fromDate, toDate);
        Member member = findMember(memberId);

        return buildReport(member, Optional.empty(), fromDate, toDate, asOf);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudyTimeReportResponse findForManager(
            Long currentMemberId,
            Long memberId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Instant asOf = clock.instant();
        validateDateRange(fromDate, toDate);
        Member manager = findMember(currentMemberId);
        if (!manager.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
        Member member = findMember(memberId);
        if (!manager.getBranchId().equals(member.getBranchId())) {
            throw MemberException.forbidden();
        }

        return buildReport(member, Optional.of(manager.getBranchId()), fromDate, toDate, asOf);
    }

    private StudyTimeReportResponse buildReport(
            Member member,
            Optional<Long> branchScope,
            LocalDate fromDate,
            LocalDate toDate,
            Instant asOf
    ) {
        Instant rangeStartedAt = fromDate.atStartOfDay(STUDY_ZONE).toInstant();
        Instant rangeEndedAt = toDate.plusDays(1).atStartOfDay(STUDY_ZONE).toInstant();
        Instant queryEndedAt = earlierOf(rangeEndedAt, asOf);

        List<StudyPresenceIntervalRow> presenceRows = findPresenceRows(
                member.getId(),
                branchScope,
                rangeStartedAt,
                queryEndedAt
        );
        List<StudyBreakIntervalRow> breakRows = findBreakRows(
                member.getId(),
                branchScope,
                fromDate,
                toDate,
                rangeStartedAt,
                queryEndedAt
        );
        Map<LocalDate, Set<StudyPeriod>> excludedPeriodsByDate = branchScope
                .map(branchId -> leaveExclusionService.findExcludedPeriods(
                        member.getId(),
                        branchId,
                        fromDate,
                        toDate
                ))
                .orElseGet(() -> leaveExclusionService.findExcludedPeriods(
                        member.getId(),
                        fromDate,
                        toDate
                ));
        Map<Long, StudyInterval> effectivePresenceBySessionId = toEffectivePresenceBySessionId(
                presenceRows,
                asOf
        );

        List<StudyTimeDailyReportResponse> days = fromDate.datesUntil(toDate.plusDays(1))
                .map(studyDate -> buildDailyReport(
                        studyDate,
                        presenceRows,
                        breakRows,
                        effectivePresenceBySessionId,
                        excludedPeriodsByDate.getOrDefault(studyDate, Set.of()),
                        asOf
                ))
                .toList();

        long totalPresenceSeconds = totalSeconds(days, DayDuration.PRESENCE);
        long totalPeriodSeconds = totalSeconds(days, DayDuration.PERIOD);
        long totalBreakSeconds = totalSeconds(days, DayDuration.BREAK);
        int attendedDayCount = (int) days.stream()
                .filter(day -> day.presenceDuration().totalSeconds() > 0L)
                .count();

        return new StudyTimeReportResponse(
                member.getId(),
                member.getBranchId(),
                STUDY_ZONE.getId(),
                fromDate,
                toDate,
                asOf,
                attendedDayCount,
                StudyTimeReportTotalsResponse.from(
                        Duration.ofSeconds(totalPresenceSeconds),
                        Duration.ofSeconds(totalPeriodSeconds),
                        Duration.ofSeconds(totalBreakSeconds)
                ),
                days
        );
    }

    private StudyTimeDailyReportResponse buildDailyReport(
            LocalDate studyDate,
            List<StudyPresenceIntervalRow> presenceRows,
            List<StudyBreakIntervalRow> breakRows,
            Map<Long, StudyInterval> effectivePresenceBySessionId,
            Set<StudyPeriod> excludedPeriods,
            Instant asOf
    ) {
        Instant dayStartedAt = studyDate.atStartOfDay(STUDY_ZONE).toInstant();
        Instant dayEndedAt = studyDate.plusDays(1).atStartOfDay(STUDY_ZONE).toInstant();
        List<StudyInterval> presenceIntervals = presenceRows.stream()
                .map(row -> effectivePresenceBySessionId.get(row.sessionId()))
                .filter(java.util.Objects::nonNull)
                .map(interval -> intersect(interval, dayStartedAt, dayEndedAt))
                .flatMap(Optional::stream)
                .toList();
        List<StudyInterval> mergedPresenceIntervals = mergeIntervals(presenceIntervals);
        List<BreakStudyInterval> breakIntervals = breakRows.stream()
                .filter(row -> row.studyDate().equals(studyDate))
                .map(row -> toBreakStudyInterval(row, effectivePresenceBySessionId, asOf))
                .flatMap(Optional::stream)
                .toList();

        DailyStudyTime calculated = studyTimeCalculator.calculate(
                studyDate,
                new StudyTimeCalculationInput(
                        mergedPresenceIntervals,
                        breakIntervals,
                        excludedPeriods
                )
        );
        Duration presenceDuration = mergedPresenceIntervals.stream()
                .map(interval -> Duration.between(interval.startedAt(), interval.endedAt()))
                .reduce(Duration.ZERO, Duration::plus);

        return StudyTimeDailyReportResponse.from(calculated, presenceDuration, excludedPeriods);
    }

    private List<StudyPresenceIntervalRow> findPresenceRows(
            Long memberId,
            Optional<Long> branchScope,
            Instant rangeStartedAt,
            Instant queryEndedAt
    ) {
        if (!queryEndedAt.isAfter(rangeStartedAt)) {
            return List.of();
        }
        if (branchScope.isPresent()) {
            return studyPresenceSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                    branchScope.get(),
                    memberId,
                    rangeStartedAt,
                    queryEndedAt
            );
        }
        return studyPresenceSessionRepository.findIntervalRowsByMemberId(
                memberId,
                rangeStartedAt,
                queryEndedAt
        );
    }

    private List<StudyBreakIntervalRow> findBreakRows(
            Long memberId,
            Optional<Long> branchScope,
            LocalDate fromDate,
            LocalDate toDate,
            Instant rangeStartedAt,
            Instant queryEndedAt
    ) {
        if (!queryEndedAt.isAfter(rangeStartedAt)) {
            return List.of();
        }
        if (branchScope.isPresent()) {
            return studyBreakSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                    branchScope.get(),
                    memberId,
                    fromDate,
                    toDate,
                    rangeStartedAt,
                    queryEndedAt
            );
        }
        return studyBreakSessionRepository.findIntervalRowsByMemberId(
                memberId,
                fromDate,
                toDate,
                rangeStartedAt,
                queryEndedAt
        );
    }

    private Map<Long, StudyInterval> toEffectivePresenceBySessionId(
            List<StudyPresenceIntervalRow> rows,
            Instant asOf
    ) {
        Map<Long, StudyInterval> intervals = new HashMap<>();
        for (StudyPresenceIntervalRow row : rows) {
            Instant endedAt = row.checkedOutAt() == null
                    ? autoClosePolicy.effectiveActiveEndAt(row.checkedInAt(), asOf)
                    : earlierOf(row.checkedOutAt(), asOf);
            if (endedAt.isAfter(row.checkedInAt())) {
                intervals.put(row.sessionId(), new StudyInterval(row.checkedInAt(), endedAt));
            }
        }
        return Map.copyOf(intervals);
    }

    private Optional<BreakStudyInterval> toBreakStudyInterval(
            StudyBreakIntervalRow row,
            Map<Long, StudyInterval> effectivePresenceBySessionId,
            Instant asOf
    ) {
        StudyInterval parentPresence = effectivePresenceBySessionId.get(row.presenceSessionId());
        if (parentPresence == null) {
            return Optional.empty();
        }

        Instant storedOrCurrentEnd = row.endedAt() == null ? asOf : row.endedAt();
        Instant endedAt = earlierOf(
                earlierOf(storedOrCurrentEnd, row.windowEndedAt()),
                parentPresence.endedAt()
        );
        Instant startedAt = laterOf(row.startedAt(), parentPresence.startedAt());
        if (!endedAt.isAfter(startedAt)) {
            return Optional.empty();
        }

        return Optional.of(new BreakStudyInterval(
                row.studyBreak(),
                new StudyInterval(startedAt, endedAt)
        ));
    }

    private List<StudyInterval> mergeIntervals(List<StudyInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }

        List<StudyInterval> sorted = intervals.stream()
                .sorted(Comparator.comparing(StudyInterval::startedAt))
                .toList();
        List<StudyInterval> merged = new ArrayList<>();
        StudyInterval current = sorted.getFirst();

        for (int index = 1; index < sorted.size(); index++) {
            StudyInterval next = sorted.get(index);
            if (next.startedAt().isAfter(current.endedAt())) {
                merged.add(current);
                current = next;
                continue;
            }

            current = new StudyInterval(
                    current.startedAt(),
                    next.endedAt().isAfter(current.endedAt()) ? next.endedAt() : current.endedAt()
            );
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private Optional<StudyInterval> intersect(StudyInterval interval, Instant startedAt, Instant endedAt) {
        Instant overlapStartedAt = interval.startedAt().isAfter(startedAt) ? interval.startedAt() : startedAt;
        Instant overlapEndedAt = interval.endedAt().isBefore(endedAt) ? interval.endedAt() : endedAt;
        if (!overlapEndedAt.isAfter(overlapStartedAt)) {
            return Optional.empty();
        }
        return Optional.of(new StudyInterval(overlapStartedAt, overlapEndedAt));
    }

    private long totalSeconds(List<StudyTimeDailyReportResponse> days, DayDuration duration) {
        return days.stream()
                .mapToLong(day -> switch (duration) {
                    case PRESENCE -> day.presenceDuration().totalSeconds();
                    case PERIOD -> day.recognizedPeriodDuration().totalSeconds();
                    case BREAK -> day.recognizedBreakDuration().totalSeconds();
                })
                .sum();
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw StudyTimeException.invalidDateRange();
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_REPORT_INCLUSIVE_DAYS) {
            throw StudyTimeException.dateRangeTooLarge();
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private Instant earlierOf(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private enum DayDuration {
        PRESENCE,
        PERIOD,
        BREAK
    }
}
