package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;

public record SharedPlanUpdateRequest(
        LocalDate weekStartDate,
        Long expectedRevision,
        String content,
        Boolean done
) {
}
