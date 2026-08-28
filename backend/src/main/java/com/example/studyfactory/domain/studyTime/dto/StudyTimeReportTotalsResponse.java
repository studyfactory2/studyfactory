package com.example.studyfactory.domain.studyTime.dto;

import java.time.Duration;

public record StudyTimeReportTotalsResponse(
        StudyTimeDurationResponse presenceDuration,
        StudyTimeDurationResponse recognizedPeriodDuration,
        StudyTimeDurationResponse recognizedBreakDuration,
        StudyTimeDurationResponse totalRecognizedStudyDuration
) {

    public static StudyTimeReportTotalsResponse from(
            Duration presenceDuration,
            Duration periodDuration,
            Duration breakDuration
    ) {
        return new StudyTimeReportTotalsResponse(
                StudyTimeDurationResponse.from(presenceDuration),
                StudyTimeDurationResponse.from(periodDuration),
                StudyTimeDurationResponse.from(breakDuration),
                StudyTimeDurationResponse.from(periodDuration.plus(breakDuration))
        );
    }
}
