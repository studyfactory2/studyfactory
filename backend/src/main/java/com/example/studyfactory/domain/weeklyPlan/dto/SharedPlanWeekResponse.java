package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;
import java.util.List;

public record SharedPlanWeekResponse(
        Long memberId,
        Long branchId,
        LocalDate weekStartDate,
        long revision,
        boolean legacySaveProtected,
        String goal,
        List<SharedPlanGroupResponse> groups,
        List<SharedPlanItemResponse> ungroupedItems
) {
}
