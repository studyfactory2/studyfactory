package com.example.studyfactory.domain.studyTime.dto;

import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record StudyTimeDailyReportResponse(
        LocalDate studyDate,
        List<StudyPeriod> excludedPeriods,
        StudyTimeDurationResponse presenceDuration,
        StudyTimeDurationResponse recognizedPeriodDuration,
        StudyTimeDurationResponse recognizedBreakDuration,
        StudyTimeDurationResponse totalRecognizedStudyDuration,
        List<StudyTimePeriodReportResponse> periods,
        List<StudyTimeBreakReportResponse> breaks
) {

    public StudyTimeDailyReportResponse {
        excludedPeriods = List.copyOf(excludedPeriods);
        periods = List.copyOf(periods);
        breaks = List.copyOf(breaks);
    }

    public static StudyTimeDailyReportResponse from(
            DailyStudyTime studyTime,
            Duration presenceDuration,
            Set<StudyPeriod> excludedPeriods
    ) {
        List<StudyPeriod> sortedExcludedPeriods = excludedPeriods.stream()
                .sorted(Comparator.comparingInt(StudyPeriod::getPeriodNumber))
                .toList();

        return new StudyTimeDailyReportResponse(
                studyTime.studyDate(),
                sortedExcludedPeriods,
                StudyTimeDurationResponse.from(presenceDuration),
                StudyTimeDurationResponse.from(studyTime.periodDuration()),
                StudyTimeDurationResponse.from(studyTime.breakDuration()),
                StudyTimeDurationResponse.from(studyTime.totalDuration()),
                studyTime.periods().stream()
                        .map(period -> StudyTimePeriodReportResponse.from(period, excludedPeriods))
                        .toList(),
                studyTime.breaks().stream()
                        .map(studyBreak -> StudyTimeBreakReportResponse.from(studyBreak, excludedPeriods))
                        .toList()
        );
    }
}
