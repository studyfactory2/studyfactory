package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDateTime;

public record DailyLeaveStatusResponse(
        Long memberId,
        Long branchId,
        Integer seatNumber,
        String name,
        String branch,
        LeaveType leaveType,
        LocalDateTime createdAt
) {
}
