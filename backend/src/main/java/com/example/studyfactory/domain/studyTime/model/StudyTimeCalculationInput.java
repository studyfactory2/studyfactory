package com.example.studyfactory.domain.studyTime.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StudyTimeCalculationInput(
        List<StudyInterval> presenceIntervals,
        List<BreakStudyInterval> breakStudyIntervals,
        Set<StudyPeriod> excludedPeriods
) {
    public StudyTimeCalculationInput {
        Objects.requireNonNull(presenceIntervals, "presenceIntervals must not be null");
        Objects.requireNonNull(breakStudyIntervals, "breakStudyIntervals must not be null");
        Objects.requireNonNull(excludedPeriods, "excludedPeriods must not be null");

        presenceIntervals = List.copyOf(presenceIntervals);
        breakStudyIntervals = List.copyOf(breakStudyIntervals);
        excludedPeriods = Set.copyOf(excludedPeriods);
    }

    public static StudyTimeCalculationInput regular(List<StudyInterval> presenceIntervals) {
        return new StudyTimeCalculationInput(presenceIntervals, List.of(), Set.of());
    }
}
