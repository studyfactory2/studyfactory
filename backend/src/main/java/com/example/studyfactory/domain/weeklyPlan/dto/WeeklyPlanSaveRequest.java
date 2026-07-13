package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyPlanSaveRequest(
        LocalDate weekStartDate,
        String goal,
        List<WeeklyPlanItemRequest> items
) {
}
