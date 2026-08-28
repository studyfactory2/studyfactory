package com.example.studyfactory.domain.studyTime.model;

import java.time.Duration;
import java.time.LocalTime;

public enum StudyPeriod {
    FIRST(1, 0, LocalTime.of(9, 0), LocalTime.of(10, 30)),
    SECOND(2, 1, LocalTime.of(10, 45), LocalTime.of(12, 5)),
    THIRD(3, 2, LocalTime.of(13, 20), LocalTime.of(14, 30)),
    FOURTH(4, 3, LocalTime.of(14, 45), LocalTime.of(16, 15)),
    FIFTH(5, 4, LocalTime.of(16, 30), LocalTime.of(17, 50)),
    SIXTH(6, 5, LocalTime.of(19, 5), LocalTime.of(20, 25)),
    SEVENTH(7, 6, LocalTime.of(20, 40), LocalTime.of(22, 0));

    private final int periodNumber;
    private final int weeklyPlanIndex;
    private final LocalTime startTime;
    private final LocalTime endTime;

    StudyPeriod(int periodNumber, int weeklyPlanIndex, LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Study period end time must be after its start time");
        }

        this.periodNumber = periodNumber;
        this.weeklyPlanIndex = weeklyPlanIndex;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public int getWeeklyPlanIndex() {
        return weeklyPlanIndex;
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
}
