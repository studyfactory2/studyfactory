package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.util.List;

/** Underlying excess request, even when O or manager leave wins the visible attendance cell. */
public record ExcessLeaveRequestResponse(
        Long leaveId,
        LeaveType leaveType,
        String label,
        List<Integer> slots,
        WeeklyLeaveAllowanceResponse weeklyAllowance
) {
    public ExcessLeaveRequestResponse {
        slots = List.copyOf(slots);
    }
}
