package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDateTime;

public record DailyLeaveStatusResponse(
        String name,
        String branch,
        LeaveType leaveType,
        LocalDateTime createdAt
) {
}
