package com.example.studyfactory.domain.attendance.dto;

import com.example.studyfactory.domain.leave.dto.ExcessLeaveRequestResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AttendanceBoardRowResponse(
        Long memberId,
        Integer seatNumber,
        String name,
        LocalDate joinDate,
        LocalDateTime createdAt,
        String certificationContent,
        List<String> slots,
        List<String> slotSources,
        List<ExcessLeaveRequestResponse> excessLeaveRequests,
        List<Integer> clearedPresentSlots
) {
    public AttendanceBoardRowResponse {
        excessLeaveRequests = List.copyOf(excessLeaveRequests);
        clearedPresentSlots = List.copyOf(clearedPresentSlots);
    }
}
