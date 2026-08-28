package com.example.studyfactory.domain.studyBreak.dto;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record StudyBreakStatusResponse(
        String zoneId,
        Instant asOf,
        LocalDate studyDate,
        boolean checkedIn,
        StudyBreakWindowResponse currentBreak,
        boolean active,
        StudyBreakSessionResponse session,
        boolean canStart,
        boolean canStop,
        StudyBreakStartBlockReason startBlockReason
) {

    public static StudyBreakStatusResponse from(
            Instant asOf,
            LocalDate studyDate,
            boolean checkedIn,
            Optional<StudyBreakWindow> currentBreak,
            Optional<StudyBreakSession> activeSession
    ) {
        StudyBreakStartBlockReason blockReason = blockReason(checkedIn, currentBreak, activeSession);
        return new StudyBreakStatusResponse(
                "Asia/Seoul",
                asOf,
                studyDate,
                checkedIn,
                currentBreak.map(StudyBreakWindowResponse::from).orElse(null),
                activeSession.isPresent(),
                activeSession.map(session -> StudyBreakSessionResponse.from(session, asOf)).orElse(null),
                blockReason == null,
                activeSession.isPresent(),
                blockReason
        );
    }

    private static StudyBreakStartBlockReason blockReason(
            boolean checkedIn,
            Optional<StudyBreakWindow> currentBreak,
            Optional<StudyBreakSession> activeSession
    ) {
        if (!checkedIn) {
            return StudyBreakStartBlockReason.NOT_CHECKED_IN;
        }
        if (activeSession.isPresent()) {
            return StudyBreakStartBlockReason.ALREADY_ACTIVE;
        }
        if (currentBreak.isEmpty()) {
            return StudyBreakStartBlockReason.OUTSIDE_BREAK;
        }
        return null;
    }
}
