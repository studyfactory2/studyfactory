package com.example.studyfactory.domain.leave.dto;

import java.time.LocalDate;

public record MonthlyLeaveCalendarResponse(
        LocalDate leaveDate,
        String label,
        String source,
        String slots
) {
}
