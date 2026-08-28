package com.example.studyfactory.domain.studyTime.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailyStudyTime(
        LocalDate studyDate,
        Duration totalDuration,
        List<PeriodStudyTime> periods
) {
    public DailyStudyTime {
        Objects.requireNonNull(studyDate, "studyDate must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        Objects.requireNonNull(periods, "periods must not be null");

        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException("Total study duration must not be negative");
        }

        periods = List.copyOf(periods);
    }

    public record PeriodStudyTime(
            StudyPeriod period,
            Duration duration
    ) {
        public PeriodStudyTime {
            Objects.requireNonNull(period, "period must not be null");
            Objects.requireNonNull(duration, "duration must not be null");

            if (duration.isNegative()) {
                throw new IllegalArgumentException("Period study duration must not be negative");
            }
        }
    }
}
