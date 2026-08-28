package com.example.studyfactory.domain.studyBreak.model;

import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;

public record StudyBreakIntervalRow(
        Long sessionId,
        Long presenceSessionId,
        Long memberId,
        Long branchId,
        LocalDate studyDate,
        StudyBreak studyBreak,
        Instant startedAt,
        Instant endedAt,
        Instant windowEndedAt
) {
}
