package com.example.studyfactory.domain.attendance.dto;

import java.util.List;

public record AttendanceBoardRowResponse(
        Integer seatNumber,
        String name,
        List<String> slots
) {
}
