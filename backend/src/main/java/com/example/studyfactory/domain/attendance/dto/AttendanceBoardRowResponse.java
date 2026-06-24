package com.example.studyfactory.domain.attendance.dto;

import java.util.List;

public record AttendanceBoardRowResponse(
        Long memberId,
        Integer seatNumber,
        String name,
        List<String> slots
) {
}
