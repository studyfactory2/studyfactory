package com.example.studyfactory.domain.attendance.dto;

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
        List<String> slotSources
) {
}
