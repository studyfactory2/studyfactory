package com.example.studyfactory.domain.weeklyPlan.dto;

import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import java.time.LocalDate;
import java.util.UUID;

public record SharedPlanCreateRequest(
        LocalDate weekStartDate,
        Long expectedRevision,
        Integer dayIndex,
        PlanGroup group,
        String content,
        UUID clientRequestId
) {
}
