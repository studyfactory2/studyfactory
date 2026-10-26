package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;

public record SharedPlanGoalRequest(
        LocalDate weekStartDate,
        Long expectedRevision,
        String goal
) {
}
