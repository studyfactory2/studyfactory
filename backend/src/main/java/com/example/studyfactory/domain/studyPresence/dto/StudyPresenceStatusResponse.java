package com.example.studyfactory.domain.studyPresence.dto;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.util.Optional;

public record StudyPresenceStatusResponse(
        boolean checkedIn,
        StudyPresenceResponse session
) {

    public static StudyPresenceStatusResponse from(Optional<StudyPresenceSession> session) {
        return session
                .map(activeSession -> new StudyPresenceStatusResponse(
                        true,
                        StudyPresenceResponse.from(activeSession)
                ))
                .orElseGet(() -> new StudyPresenceStatusResponse(false, null));
    }
}
