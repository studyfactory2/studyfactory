package com.example.studyfactory.domain.weeklyPlan.dto;

import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import com.example.studyfactory.domain.weeklyPlan.model.PlanItemSource;

public record SharedPlanItemResponse(
        Long id,
        PlanItemSource source,
        int dayIndex,
        Integer periodIndex,
        PlanGroup group,
        String content,
        boolean done,
        int sortOrder
) {
}
