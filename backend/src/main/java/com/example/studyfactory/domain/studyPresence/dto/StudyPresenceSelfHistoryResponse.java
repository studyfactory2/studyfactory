package com.example.studyfactory.domain.studyPresence.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyPresenceSelfHistoryResponse(
        Long memberId,
        LocalDate fromDate,
        LocalDate toDate,
        String zoneId,
        Instant asOf,
        int sessionCount,
        StudyPresenceDurationResponse totalPresenceDuration,
        List<StudyPresenceSelfSessionResponse> sessions
) {

    public StudyPresenceSelfHistoryResponse {
        sessions = List.copyOf(sessions);
    }
}
