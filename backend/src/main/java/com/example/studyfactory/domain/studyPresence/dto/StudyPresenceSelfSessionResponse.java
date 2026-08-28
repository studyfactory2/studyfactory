package com.example.studyfactory.domain.studyPresence.dto;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.time.Instant;

public record StudyPresenceSelfSessionResponse(
        Long sessionId,
        Long branchId,
        Instant checkedInAt,
        Instant checkedOutAt,
        boolean currentlyActive,
        Instant overlapStartedAt,
        Instant overlapEndedAt,
        StudyPresenceDurationResponse presenceDuration
) {

    public static StudyPresenceSelfSessionResponse from(
            StudyPresenceSession session,
            Instant windowStartedAt,
            Instant windowEndedAt,
            Instant asOf,
            Instant pendingAutomaticCheckoutAt
    ) {
        boolean virtuallyAutomaticallyClosed = session.isActive() && pendingAutomaticCheckoutAt != null;
        Instant effectiveCheckedOutAt = virtuallyAutomaticallyClosed
                ? pendingAutomaticCheckoutAt
                : session.getCheckedOutAt();
        Instant effectiveSessionEnd = effectiveCheckedOutAt == null
                ? asOf
                : earlierOf(effectiveCheckedOutAt, asOf);
        Instant overlapStartedAt = laterOf(session.getCheckedInAt(), windowStartedAt);
        Instant overlapEndedAt = earlierOf(effectiveSessionEnd, windowEndedAt);
        if (overlapEndedAt.isBefore(overlapStartedAt)) {
            overlapEndedAt = overlapStartedAt;
        }

        return new StudyPresenceSelfSessionResponse(
                session.getId(),
                session.getBranchId(),
                session.getCheckedInAt(),
                effectiveCheckedOutAt,
                session.isActive() && !virtuallyAutomaticallyClosed,
                overlapStartedAt,
                overlapEndedAt,
                StudyPresenceDurationResponse.between(overlapStartedAt, overlapEndedAt)
        );
    }

    private static Instant earlierOf(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
