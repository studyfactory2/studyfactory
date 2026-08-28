package com.example.studyfactory.domain.studyTime.model;

import java.util.Objects;

public record BreakStudyInterval(
        StudyBreak studyBreak,
        StudyInterval interval
) {
    public BreakStudyInterval {
        Objects.requireNonNull(studyBreak, "studyBreak must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
    }
}
