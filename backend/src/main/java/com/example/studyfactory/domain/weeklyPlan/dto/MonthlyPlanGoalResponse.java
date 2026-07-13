package com.example.studyfactory.domain.weeklyPlan.dto;

import com.example.studyfactory.domain.weeklyPlan.entity.MonthlyPlanGoal;

public record MonthlyPlanGoalResponse(
        Long id,
        Long memberId,
        Long branchId,
        String month,
        String goal
) {

    public static MonthlyPlanGoalResponse empty(Long memberId, Long branchId, String month) {
        return new MonthlyPlanGoalResponse(null, memberId, branchId, month, "");
    }

    public static MonthlyPlanGoalResponse from(MonthlyPlanGoal goal) {
        return new MonthlyPlanGoalResponse(
                goal.getId(),
                goal.getMemberId(),
                goal.getBranchId(),
                goal.getMonth(),
                goal.getGoal()
        );
    }
}
