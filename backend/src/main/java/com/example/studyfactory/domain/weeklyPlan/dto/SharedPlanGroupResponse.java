package com.example.studyfactory.domain.weeklyPlan.dto;

import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import java.util.List;

public record SharedPlanGroupResponse(
        PlanGroup group,
        List<SharedPlanItemResponse> items
) {
}
