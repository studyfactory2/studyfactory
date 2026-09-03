package com.example.studyfactory.domain.studyPresence.dto;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCheckInMethod;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCloseReason;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.time.Instant;

public record StudyPresenceManagerSessionResponse(
        Long sessionId,
        Long memberId,
        String memberName,
        MemberRole memberRole,
        Integer seatNumber,
        Long branchId,
        Instant checkedInAt,
        StudyPresenceCheckInMethod checkInMethod,
        Long checkedInByMemberId,
        String manualCheckInReason,
        Instant checkedOutAt,
        StudyPresenceCloseReason closeReason,
        Long closedByMemberId,
        StudyPresenceCheckoutMethod checkoutMethod,
        boolean currentlyActive,
        Instant overlapStartedAt,
        Instant overlapEndedAt,
        StudyPresenceDurationResponse presenceDuration
) {

    public static StudyPresenceManagerSessionResponse from(
            StudyPresenceSession session,
            Member member,
            Instant windowStart,
            Instant windowEnd,
            Instant asOf
    ) {
        return from(session, member, windowStart, windowEnd, asOf, null);
    }

    public static StudyPresenceManagerSessionResponse from(
            StudyPresenceSession session,
            Member member,
            Instant windowStart,
            Instant windowEnd,
            Instant asOf,
            Instant pendingAutomaticCheckoutAt
    ) {
        boolean virtuallyAutomaticallyClosed = session.isActive() && pendingAutomaticCheckoutAt != null;
        Instant effectiveCheckedOutAt = virtuallyAutomaticallyClosed
                ? pendingAutomaticCheckoutAt
                : session.getCheckedOutAt();
        Instant rawSessionEnd = effectiveCheckedOutAt == null ? asOf : effectiveCheckedOutAt;
        Instant sessionEnd = rawSessionEnd.isAfter(asOf) ? asOf : rawSessionEnd;
        Instant overlapStartedAt = laterOf(session.getCheckedInAt(), windowStart);
        Instant overlapEndedAt = earlierOf(sessionEnd, windowEnd);
        if (overlapEndedAt.isBefore(overlapStartedAt)) {
            overlapEndedAt = overlapStartedAt;
        }
        Member sessionBranchMember = member != null && session.getBranchId().equals(member.getBranchId())
                ? member
                : null;

        return new StudyPresenceManagerSessionResponse(
                session.getId(),
                session.getMemberId(),
                sessionBranchMember == null ? null : sessionBranchMember.getName(),
                sessionBranchMember == null ? null : sessionBranchMember.getRole(),
                sessionBranchMember == null ? null : sessionBranchMember.getSeatNumber(),
                session.getBranchId(),
                session.getCheckedInAt(),
                session.getCheckInMethod(),
                session.getCheckedInByMemberId(),
                session.getManualCheckInReason(),
                effectiveCheckedOutAt,
                virtuallyAutomaticallyClosed ? StudyPresenceCloseReason.CHECK_OUT : session.getCloseReason(),
                virtuallyAutomaticallyClosed ? null : session.getClosedByMemberId(),
                checkoutMethodOf(session, virtuallyAutomaticallyClosed),
                session.isActive() && !virtuallyAutomaticallyClosed,
                overlapStartedAt,
                overlapEndedAt,
                StudyPresenceDurationResponse.between(overlapStartedAt, overlapEndedAt)
        );
    }

    private static StudyPresenceCheckoutMethod checkoutMethodOf(
            StudyPresenceSession session,
            boolean virtuallyAutomaticallyClosed
    ) {
        if (virtuallyAutomaticallyClosed || session.isAutomaticallyClosed()) {
            return StudyPresenceCheckoutMethod.AUTO_MIDNIGHT;
        }
        if (session.isActive()) {
            return null;
        }
        if (session.getCloseReason() == StudyPresenceCloseReason.MEMBER_DELETED) {
            return StudyPresenceCheckoutMethod.MEMBER_DELETED;
        }
        if (session.getClosedByMemberId() != null) {
            return StudyPresenceCheckoutMethod.MANAGER;
        }
        return StudyPresenceCheckoutMethod.QR;
    }

    private static Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static Instant earlierOf(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
