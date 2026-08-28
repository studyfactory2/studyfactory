package com.example.studyfactory.domain.studyPresence.dto;

import java.time.Instant;
import java.util.List;

public record StudyPresenceLiveResponse(
        Long branchId,
        String zoneId,
        Instant asOf,
        int memberCount,
        List<StudyPresenceManagerSessionResponse> sessions
) {

    public StudyPresenceLiveResponse {
        sessions = List.copyOf(sessions);
    }
}
