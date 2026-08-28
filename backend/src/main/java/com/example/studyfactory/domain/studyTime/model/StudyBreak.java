package com.example.studyfactory.domain.studyTime.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Set;

public enum StudyBreak {
    AFTER_FIRST(StudyPeriod.FIRST, StudyPeriod.SECOND, LocalTime.of(10, 30), LocalTime.of(10, 45)),
    LUNCH(StudyPeriod.SECOND, StudyPeriod.THIRD, LocalTime.of(12, 5), LocalTime.of(13, 20)),
    AFTER_THIRD(StudyPeriod.THIRD, StudyPeriod.FOURTH, LocalTime.of(14, 30), LocalTime.of(14, 45)),
    AFTER_FOURTH(StudyPeriod.FOURTH, StudyPeriod.FIFTH, LocalTime.of(16, 15), LocalTime.of(16, 30)),
    DINNER(StudyPeriod.FIFTH, StudyPeriod.SIXTH, LocalTime.of(17, 50), LocalTime.of(19, 5)),
    AFTER_SIXTH(StudyPeriod.SIXTH, StudyPeriod.SEVENTH, LocalTime.of(20, 25), LocalTime.of(20, 40));

    private final StudyPeriod previousPeriod;
    private final StudyPeriod nextPeriod;
    private final LocalTime startTime;
    private final LocalTime endTime;

    StudyBreak(
            StudyPeriod previousPeriod,
            StudyPeriod nextPeriod,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (!startTime.equals(previousPeriod.getEndTime()) || !endTime.equals(nextPeriod.getStartTime())) {
            throw new IllegalArgumentException("Study break must exactly connect its neighboring periods");
        }

        this.previousPeriod = previousPeriod;
        this.nextPeriod = nextPeriod;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public StudyPeriod getPreviousPeriod() {
        return previousPeriod;
    }

    public StudyPeriod getNextPeriod() {
        return nextPeriod;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public boolean isExcludedBy(Set<StudyPeriod> excludedPeriods) {
        return excludedPeriods.contains(previousPeriod) && excludedPeriods.contains(nextPeriod);
    }
}
