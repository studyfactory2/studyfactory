package com.example.studyfactory.domain.leave.dto;

import java.time.LocalDate;

/** Informational weekly allowance, measured in half-day units; never a submission limit. */
public record WeeklyLeaveAllowanceResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        int allowanceUnits,
        int requestUnits,
        int usedUnitsBefore,
        int usedUnitsAfter,
        int totalUnits,
        boolean excess
) {}
