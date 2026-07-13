package com.example.studyfactory.domain.weeklyPlan.dto;

public record WeeklyPlanItemRequest(
        int periodIndex,
        int dayIndex,
        String content,
        boolean done,
        int sortOrder
) {
}
