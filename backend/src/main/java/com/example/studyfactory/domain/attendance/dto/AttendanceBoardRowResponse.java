package com.example.studyfactory.domain.attendance.dto;

import java.time.LocalDate;
import java.util.List;

public record AttendanceBoardRowResponse(
        Long memberId,
        Integer seatNumber,
        String name,
        LocalDate joinDate,
        String certificationContent,
        List<String> slots
) {
}
