package com.example.studyfactory.domain.leave.dto;

import java.time.LocalDate;

public record FixedLeaveGenerationResponse(
        LocalDate startDate,
        LocalDate endDate,
        int createdCount
) {
}
