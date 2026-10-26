package com.example.studyfactory.domain.weeklyPlan.dto;

import java.time.LocalDate;
import java.util.UUID;

public record BreakPlanCreateRequest(
        LocalDate weekStartDate,
        Long expectedRevision,
        Integer dayIndex,
        Integer periodIndex,
        String content,
        UUID clientRequestId
) {
}
