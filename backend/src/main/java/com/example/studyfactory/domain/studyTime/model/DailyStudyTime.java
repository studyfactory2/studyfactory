package com.example.studyfactory.domain.studyTime.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailyStudyTime(
        LocalDate studyDate,
        Duration periodDuration,
        Duration breakDuration,
        Duration totalDuration,
        List<PeriodStudyTime> periods,
        List<BreakStudyTime> breaks
) {
    public DailyStudyTime {
        Objects.requireNonNull(studyDate, "studyDate must not be null");
        Objects.requireNonNull(periodDuration, "periodDuration must not be null");
        Objects.requireNonNull(breakDuration, "breakDuration must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        Objects.requireNonNull(periods, "periods must not be null");
        Objects.requireNonNull(breaks, "breaks must not be null");

        if (periodDuration.isNegative() || breakDuration.isNegative() || totalDuration.isNegative()) {
            throw new IllegalArgumentException("Study durations must not be negative");
        }
        if (!periodDuration.plus(breakDuration).equals(totalDuration)) {
            throw new IllegalArgumentException("Total study duration must equal period plus break duration");
        }

        periods = List.copyOf(periods);
        breaks = List.copyOf(breaks);
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

    public record BreakStudyTime(
            StudyBreak studyBreak,
            Duration duration
    ) {
        public BreakStudyTime {
            Objects.requireNonNull(studyBreak, "studyBreak must not be null");
            Objects.requireNonNull(duration, "duration must not be null");

            if (duration.isNegative()) {
                throw new IllegalArgumentException("Break study duration must not be negative");
            }
        }
    }
}
