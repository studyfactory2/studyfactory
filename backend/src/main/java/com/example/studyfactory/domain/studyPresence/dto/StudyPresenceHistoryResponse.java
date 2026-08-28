package com.example.studyfactory.domain.studyPresence.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyPresenceHistoryResponse(
        Long branchId,
        Long memberId,
        LocalDate fromDate,
        LocalDate toDate,
        String zoneId,
        Instant asOf,
        int sessionCount,
        StudyPresenceDurationResponse totalPresenceDuration,
        List<StudyPresenceManagerSessionResponse> sessions
) {

    public StudyPresenceHistoryResponse {
        sessions = List.copyOf(sessions);
    }
}
