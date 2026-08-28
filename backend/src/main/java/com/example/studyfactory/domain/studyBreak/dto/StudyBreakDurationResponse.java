package com.example.studyfactory.domain.studyBreak.dto;

import java.time.Duration;
import java.time.Instant;

public record StudyBreakDurationResponse(
        long totalSeconds,
        long hours,
        int minutes,
        int seconds,
        String formatted
) {

    public static StudyBreakDurationResponse between(Instant startedAt, Instant endedAt) {
        return fromTotalSeconds(Duration.between(startedAt, endedAt).getSeconds());
    }

    public static StudyBreakDurationResponse fromTotalSeconds(long totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException("totalSeconds must not be negative");
        }

        long hours = totalSeconds / 3_600;
        int minutes = (int) ((totalSeconds % 3_600) / 60);
        int seconds = (int) (totalSeconds % 60);
        return new StudyBreakDurationResponse(
                totalSeconds,
                hours,
                minutes,
                seconds,
                "%02d:%02d:%02d".formatted(hours, minutes, seconds)
        );
    }
}
