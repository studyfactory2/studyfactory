package com.example.studyfactory.domain.studyTime.dto;

import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.LocalTime;
import java.util.Set;

public record StudyTimeBreakReportResponse(
        StudyBreak studyBreak,
        LocalTime startsAt,
        LocalTime endsAt,
        boolean excludedByLeave,
        StudyTimeDurationResponse recognizedDuration
) {

    public static StudyTimeBreakReportResponse from(
            DailyStudyTime.BreakStudyTime studyTime,
            Set<StudyPeriod> excludedPeriods
    ) {
        StudyBreak studyBreak = studyTime.studyBreak();
        return new StudyTimeBreakReportResponse(
                studyBreak,
                studyBreak.getStartTime(),
                studyBreak.getEndTime(),
                studyBreak.isExcludedBy(excludedPeriods),
                StudyTimeDurationResponse.from(studyTime.duration())
        );
    }
}
