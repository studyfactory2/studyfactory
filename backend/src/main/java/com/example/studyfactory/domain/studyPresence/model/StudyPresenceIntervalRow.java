package com.example.studyfactory.domain.studyPresence.model;

import java.time.Instant;

public record StudyPresenceIntervalRow(
        Long sessionId,
        Long memberId,
        Long branchId,
        Instant checkedInAt,
        Instant checkedOutAt
) {
}
