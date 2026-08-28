package com.example.studyfactory.domain.studyPresence.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public record StudyPresenceDurationResponse(
        long totalSeconds,
        long hours,
        int minutes,
        int seconds,
        String formatted
) {

    public static StudyPresenceDurationResponse between(Instant start, Instant end) {
        return fromTotalSeconds(Duration.between(start, end).getSeconds());
    }

    public static StudyPresenceDurationResponse fromTotalSeconds(long secondsToFormat) {
        long totalSeconds = Math.max(0L, secondsToFormat);
        long hours = totalSeconds / 3_600;
        int minutes = (int) ((totalSeconds % 3_600) / 60);
        int seconds = (int) (totalSeconds % 60);

        return new StudyPresenceDurationResponse(
                totalSeconds,
                hours,
                minutes,
                seconds,
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        );
    }
}
