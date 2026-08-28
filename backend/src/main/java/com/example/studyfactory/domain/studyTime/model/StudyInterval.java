package com.example.studyfactory.domain.studyTime.model;

import java.time.Instant;
import java.util.Objects;

public record StudyInterval(
        Instant startedAt,
        Instant endedAt
) {
    public StudyInterval {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Study interval end must not be before its start");
        }
    }
}
