package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
        Long id,
        Long memberId,
        Long branchId,
        LocalDate leaveDate,
        LeaveType leaveType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        WeeklyLeaveAllowanceResponse weeklyAllowance
) {

    public static LeaveResponse from(LeaveRequest leaveRequest, WeeklyLeaveAllowanceResponse weeklyAllowance) {
        return new LeaveResponse(
                leaveRequest.getId(),
                leaveRequest.getMemberId(),
                leaveRequest.getBranchId(),
                leaveRequest.getLeaveDate(),
                leaveRequest.getLeaveType(),
                leaveRequest.getCreatedAt(),
                leaveRequest.getUpdatedAt(),
                weeklyAllowance
        );
    }
}
