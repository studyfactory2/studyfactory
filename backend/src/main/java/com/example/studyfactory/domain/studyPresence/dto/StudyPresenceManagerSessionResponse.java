package com.example.studyfactory.domain.studyPresence.dto;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
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
        Instant rawSessionEnd = session.getCheckedOutAt() == null ? asOf : session.getCheckedOutAt();
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
                session.getCheckedOutAt(),
                session.getCloseReason(),
                session.getClosedByMemberId(),
                checkoutMethodOf(session),
                session.isActive(),
                overlapStartedAt,
                overlapEndedAt,
                StudyPresenceDurationResponse.between(overlapStartedAt, overlapEndedAt)
        );
    }

    private static StudyPresenceCheckoutMethod checkoutMethodOf(StudyPresenceSession session) {
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
