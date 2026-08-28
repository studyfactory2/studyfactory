package com.example.studyfactory.domain.studyTime.dto;

import java.time.Duration;
import java.util.Locale;

public record StudyTimeDurationResponse(
        long totalSeconds,
        long hours,
        int minutes,
        int seconds,
        String formatted
) {

    public static StudyTimeDurationResponse from(Duration duration) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be null or negative");
        }

        return fromTotalSeconds(duration.getSeconds());
    }

    public static StudyTimeDurationResponse fromTotalSeconds(long secondsToFormat) {
        if (secondsToFormat < 0L) {
            throw new IllegalArgumentException("totalSeconds must not be negative");
        }

        long hours = secondsToFormat / 3_600;
        int minutes = (int) ((secondsToFormat % 3_600) / 60);
        int seconds = (int) (secondsToFormat % 60);

        return new StudyTimeDurationResponse(
                secondsToFormat,
                hours,
                minutes,
                seconds,
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        );
    }
}
