package com.example.studyfactory.domain.studyBreak.model;

import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record StudyBreakWindow(
        LocalDate studyDate,
        StudyBreak studyBreak,
        Instant startedAt,
        Instant endedAt
) {
    public StudyBreakWindow {
        Objects.requireNonNull(studyDate, "studyDate must not be null");
        Objects.requireNonNull(studyBreak, "studyBreak must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        if (!endedAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("Break window end must be after its start");
        }
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startedAt) && instant.isBefore(endedAt);
    }
}
