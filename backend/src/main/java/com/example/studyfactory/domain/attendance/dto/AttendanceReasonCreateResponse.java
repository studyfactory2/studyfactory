package com.example.studyfactory.domain.attendance.dto;

public record AttendanceReasonCreateResponse(
        Long memberId,
        Long branchId,
        int requestedPeriodCount,
        int appliedPeriodCount,
        int preservedPresentCount
) {
}
