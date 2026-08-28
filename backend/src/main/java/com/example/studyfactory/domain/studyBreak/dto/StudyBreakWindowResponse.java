package com.example.studyfactory.domain.studyBreak.dto;

import com.example.studyfactory.domain.studyBreak.model.StudyBreakWindow;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;

public record StudyBreakWindowResponse(
        StudyBreak studyBreak,
        Instant startsAt,
        Instant endsAt
) {

    public static StudyBreakWindowResponse from(StudyBreakWindow window) {
        return new StudyBreakWindowResponse(
                window.studyBreak(),
                window.startedAt(),
                window.endedAt()
        );
    }
}
