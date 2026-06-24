package com.example.studyfactory.domain.attendance.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyAttendanceBoardResponse(
        LocalDate date,
        List<AttendanceBoardRowResponse> rows
) {
}
