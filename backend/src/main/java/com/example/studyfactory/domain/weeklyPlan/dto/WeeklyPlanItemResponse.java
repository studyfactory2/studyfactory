package com.example.studyfactory.domain.weeklyPlan.dto;

import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;

public record WeeklyPlanItemResponse(
        Long id,
        int periodIndex,
        int dayIndex,
        String content,
        boolean done,
        int sortOrder
) {

    public static WeeklyPlanItemResponse from(WeeklyPlanItem item) {
        return new WeeklyPlanItemResponse(
                item.getId(),
                item.getPeriodIndex(),
                item.getDayIndex(),
                item.getContent(),
                item.isDone(),
                item.getSortOrder()
        );
    }
}
