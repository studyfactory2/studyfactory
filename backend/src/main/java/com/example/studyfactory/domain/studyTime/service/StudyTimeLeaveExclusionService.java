package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import com.example.studyfactory.domain.leave.service.EffectiveLeaveService;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyTimeLeaveExclusionService {

    private static final Map<Integer, StudyPeriod> PERIOD_BY_SLOT = Arrays.stream(StudyPeriod.values())
            .collect(Collectors.toUnmodifiableMap(StudyPeriod::getPeriodNumber, period -> period));

    private final EffectiveLeaveService effectiveLeaveService;
    private final AttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            Long memberId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validate(memberId, fromDate, toDate);

        return findExcludedPeriods(
                fromDate,
                toDate,
                effectiveLeaveService.findForMember(memberId, fromDate, toDate),
                attendanceRepository.findByMemberIdAndAttendanceDateBetween(memberId, fromDate, toDate)
        );
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            Long memberId,
            Long branchId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validate(memberId, fromDate, toDate);
        Objects.requireNonNull(branchId, "branchId must not be null");

        return findExcludedPeriods(
                fromDate,
                toDate,
                effectiveLeaveService.findForMember(memberId, branchId, fromDate, toDate),
                attendanceRepository.findByMemberIdAndBranchIdAndAttendanceDateBetween(
                        memberId, branchId, fromDate, toDate
                )
        );
    }

    private Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            LocalDate fromDate,
            LocalDate toDate,
            List<EffectiveLeaveOccurrence> leaves,
            List<Attendance> attendances
    ) {
        Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods = initializeDates(fromDate, toDate);
        for (EffectiveLeaveOccurrence leave : leaves) {
            EnumSet<StudyPeriod> periods = excludedPeriods.get(leave.date());
            if (periods != null) {
                leave.slots().stream().map(PERIOD_BY_SLOT::get).filter(Objects::nonNull).forEach(periods::add);
            }
        }
        attendances.forEach(attendance -> applyAttendanceOverride(excludedPeriods, attendance));

        return immutableCopy(excludedPeriods);
    }

    private void validate(Long memberId, LocalDate fromDate, LocalDate toDate) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    private Map<LocalDate, EnumSet<StudyPeriod>> initializeDates(LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, EnumSet<StudyPeriod>> periodsByDate = new LinkedHashMap<>();
        LocalDate date = fromDate;
        while (true) {
            periodsByDate.put(date, EnumSet.noneOf(StudyPeriod.class));
            if (date.equals(toDate)) {
                return periodsByDate;
            }
            date = date.plusDays(1);
        }
    }

    private void applyAttendanceOverride(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods,
            Attendance attendance
    ) {
        EnumSet<StudyPeriod> periods = excludedPeriods.get(attendance.getAttendanceDate());
        StudyPeriod period = PERIOD_BY_SLOT.get(attendance.getSlot());
        if (periods != null && period != null) {
            periods.remove(period);
        }
    }

    private Map<LocalDate, Set<StudyPeriod>> immutableCopy(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods
    ) {
        Map<LocalDate, Set<StudyPeriod>> result = new LinkedHashMap<>();
        excludedPeriods.forEach((date, periods) -> result.put(
                date,
                periods.isEmpty()
                        ? Set.of()
                        : Collections.unmodifiableSet(EnumSet.copyOf(periods))
        ));
        return Collections.unmodifiableMap(result);
    }
}
