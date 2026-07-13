package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyPlanResponse(
        Long goalId,
        Long memberId,
        Long branchId,
        LocalDate weekStartDate,
        String goal,
        List<WeeklyPlanItemResponse> items
) {
}
