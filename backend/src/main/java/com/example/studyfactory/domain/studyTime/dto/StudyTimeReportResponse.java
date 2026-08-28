package com.example.studyfactory.domain.studyTime.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudyTimeReportResponse(
        Long memberId,
        Long branchId,
        String zoneId,
        LocalDate fromDate,
        LocalDate toDate,
        Instant asOf,
        int attendedDayCount,
        StudyTimeReportTotalsResponse totals,
        List<StudyTimeDailyReportResponse> days
) {

    public StudyTimeReportResponse {
        days = List.copyOf(days);
    }
}
