package com.example.studyfactory.domain.studyBreak.dto;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;

public record StudyBreakSessionResponse(
        Long sessionId,
        Long presenceSessionId,
        Long branchId,
        LocalDate studyDate,
        StudyBreak studyBreak,
        Instant windowStartedAt,
        Instant windowEndedAt,
        Instant startedAt,
        Instant endedAt,
        StudyBreakEndReason endReason,
        boolean active,
        StudyBreakDurationResponse elapsedDuration
) {

    public static StudyBreakSessionResponse from(StudyBreakSession session, Instant asOf) {
        Instant effectiveEndedAt = session.getEndedAt() == null
                ? earlierOf(asOf, session.getWindowEndedAt())
                : session.getEndedAt();
        if (effectiveEndedAt.isBefore(session.getStartedAt())) {
            effectiveEndedAt = session.getStartedAt();
        }

        return new StudyBreakSessionResponse(
                session.getId(),
                session.getPresenceSessionId(),
                session.getBranchId(),
                session.getStudyDate(),
                session.getStudyBreak(),
                session.getWindowStartedAt(),
                session.getWindowEndedAt(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getEndReason(),
                session.isActive() && asOf.isBefore(session.getWindowEndedAt()),
                StudyBreakDurationResponse.between(session.getStartedAt(), effectiveEndedAt)
        );
    }

    private static Instant earlierOf(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
