package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyLeaveStatusResponse(
        Long memberId,
        Long branchId,
        Integer seatNumber,
        String name,
        String branch,
        LocalDate leaveDate,
        LeaveType leaveType,
        LocalDateTime createdAt,
        String label,
        String source,
        Boolean requestedAfterEight
) {

    public DailyLeaveStatusResponse(
            Long memberId,
            Long branchId,
            Integer seatNumber,
            String name,
            String branch,
            LocalDate leaveDate,
            LeaveType leaveType,
            LocalDateTime createdAt
    ) {
        this(memberId, branchId, seatNumber, name, branch, leaveDate, leaveType, createdAt, null, "LEAVE", null);
    }
}
