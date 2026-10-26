package com.example.studyfactory.domain.weeklyPlan.model;

public enum PlanGroup {
    PERIODS_1_2,
    PERIODS_3_5,
    PERIODS_6_7;

    public static PlanGroup fromPeriodIndex(int periodIndex) {
        return switch (periodIndex) {
            case 0, 1 -> PERIODS_1_2;
            case 2, 3, 4 -> PERIODS_3_5;
            case 5, 6 -> PERIODS_6_7;
            default -> null;
        };
    }
}
