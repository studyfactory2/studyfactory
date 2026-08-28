package com.example.studyfactory.domain.studyPresence.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceDurationResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceHistoryResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceLiveResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceManagerSessionResponse;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPresenceQueryService {

    private static final ZoneId PRESENCE_ZONE = ZoneId.of("Asia/Seoul");
    private static final long MAX_HISTORY_INCLUSIVE_DAYS = 366;

    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final MemberRepository memberRepository;
    private final StudyPresenceAutoClosePolicy autoClosePolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StudyPresenceLiveResponse findLive(Long currentMemberId) {
        Member manager = findOperationsMember(currentMemberId);
        Instant asOf = clock.instant();
        List<StudyPresenceSession> sessions = studyPresenceSessionRepository
                .findActiveByBranchId(manager.getBranchId())
                .stream()
                .filter(session -> !autoClosePolicy.shouldAutomaticallyClose(session, asOf))
                .toList();
        Map<Long, Member> membersById = findMembersById(sessions);

        List<StudyPresenceManagerSessionResponse> responses = sessions.stream()
                .map(session -> StudyPresenceManagerSessionResponse.from(
                        session,
                        membersById.get(session.getMemberId()),
                        session.getCheckedInAt(),
                        asOf,
                        asOf
                ))
                .toList();

        return new StudyPresenceLiveResponse(
                manager.getBranchId(),
                PRESENCE_ZONE.getId(),
                asOf,
                responses.size(),
                responses
        );
    }

    @Transactional(readOnly = true)
    public StudyPresenceHistoryResponse findDailyHistory(Long currentMemberId, LocalDate date) {
        Member manager = findOperationsMember(currentMemberId);
        LocalDate requestedDate = date == null ? LocalDate.now(clock.withZone(PRESENCE_ZONE)) : date;
        return findHistory(manager.getBranchId(), null, requestedDate, requestedDate);
    }

    @Transactional(readOnly = true)
    public StudyPresenceHistoryResponse findMemberHistory(
            Long currentMemberId,
            Long memberId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Member manager = findOperationsMember(currentMemberId);
        validateDateRange(fromDate, toDate);

        return findHistory(manager.getBranchId(), memberId, fromDate, toDate);
    }

    private StudyPresenceHistoryResponse findHistory(
            Long branchId,
            Long memberId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateDateRange(fromDate, toDate);
        Instant asOf = clock.instant();
        Instant windowStart = fromDate.atStartOfDay(PRESENCE_ZONE).toInstant();
        Instant windowEnd = toDate.plusDays(1).atStartOfDay(PRESENCE_ZONE).toInstant();
        Instant queryEnd = asOf.isBefore(windowEnd) ? asOf : windowEnd;

        List<StudyPresenceSession> sessions;
        if (!queryEnd.isAfter(windowStart)) {
            sessions = List.of();
        } else if (memberId == null) {
            sessions = studyPresenceSessionRepository.findOverlappingByBranchId(
                    branchId,
                    windowStart,
                    queryEnd
            );
        } else {
            sessions = studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                    branchId,
                    memberId,
                    windowStart,
                    queryEnd
            );
        }
        sessions = sessions.stream()
                .filter(session -> overlapsHistoryWindow(session, windowStart, asOf))
                .toList();

        Map<Long, Member> membersById = findMembersById(sessions);
        List<StudyPresenceManagerSessionResponse> responses = sessions.stream()
                .map(session -> toHistoryResponse(
                        session,
                        membersById.get(session.getMemberId()),
                        windowStart,
                        windowEnd,
                        asOf
                ))
                .toList();
        long totalSeconds = responses.stream()
                .map(StudyPresenceManagerSessionResponse::presenceDuration)
                .mapToLong(StudyPresenceDurationResponse::totalSeconds)
                .sum();

        return new StudyPresenceHistoryResponse(
                branchId,
                memberId,
                fromDate,
                toDate,
                PRESENCE_ZONE.getId(),
                asOf,
                responses.size(),
                StudyPresenceDurationResponse.fromTotalSeconds(totalSeconds),
                responses
        );
    }

    private Member findOperationsMember(Long currentMemberId) {
        Member currentMember = memberRepository.findById(currentMemberId)
                .orElseThrow(MemberException::memberNotFound);
        if (!currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
        return currentMember;
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw StudyPresenceException.invalidDateRange();
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_HISTORY_INCLUSIVE_DAYS) {
            throw StudyPresenceException.dateRangeTooLarge();
        }
    }

    private Map<Long, Member> findMembersById(List<StudyPresenceSession> sessions) {
        List<Long> memberIds = sessions.stream()
                .map(StudyPresenceSession::getMemberId)
                .distinct()
                .toList();
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private StudyPresenceManagerSessionResponse toHistoryResponse(
            StudyPresenceSession session,
            Member member,
            Instant windowStart,
            Instant windowEnd,
            Instant asOf
    ) {
        Instant pendingAutomaticCheckoutAt = autoClosePolicy.shouldAutomaticallyClose(session, asOf)
                ? autoClosePolicy.firstMidnightAfter(session.getCheckedInAt())
                : null;
        return StudyPresenceManagerSessionResponse.from(
                session,
                member,
                windowStart,
                windowEnd,
                asOf,
                pendingAutomaticCheckoutAt
        );
    }

    private boolean overlapsHistoryWindow(
            StudyPresenceSession session,
            Instant windowStart,
            Instant asOf
    ) {
        if (!autoClosePolicy.shouldAutomaticallyClose(session, asOf)) {
            return true;
        }
        return autoClosePolicy.firstMidnightAfter(session.getCheckedInAt()).isAfter(windowStart);
    }
}
