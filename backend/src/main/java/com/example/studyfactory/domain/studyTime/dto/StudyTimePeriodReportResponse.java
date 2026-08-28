package com.example.studyfactory.domain.studyTime.dto;

import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.LocalTime;
import java.util.Set;

public record StudyTimePeriodReportResponse(
        StudyPeriod period,
        int periodNumber,
        int weeklyPlanIndex,
        LocalTime startsAt,
        LocalTime endsAt,
        boolean excludedByLeave,
        StudyTimeDurationResponse recognizedDuration
) {

    public static StudyTimePeriodReportResponse from(
            DailyStudyTime.PeriodStudyTime studyTime,
            Set<StudyPeriod> excludedPeriods
    ) {
        StudyPeriod period = studyTime.period();
        return new StudyTimePeriodReportResponse(
                period,
                period.getPeriodNumber(),
                period.getWeeklyPlanIndex(),
                period.getStartTime(),
                period.getEndTime(),
                excludedPeriods.contains(period),
                StudyTimeDurationResponse.from(studyTime.duration())
        );
    }
}
