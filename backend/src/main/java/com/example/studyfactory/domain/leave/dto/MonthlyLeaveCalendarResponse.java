package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import java.time.LocalDate;

public record MonthlyLeaveCalendarResponse(
        LocalDate leaveDate,
        String label,
        String source,
        String slots,
        LeaveSourceType sourceType,
        Long sourceId
) {
}
