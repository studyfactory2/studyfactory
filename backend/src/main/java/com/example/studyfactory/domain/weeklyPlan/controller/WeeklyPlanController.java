package com.example.studyfactory.domain.weeklyPlan.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanSaveRequest;
import com.example.studyfactory.domain.weeklyPlan.service.WeeklyPlanService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weekly-plans")
public class WeeklyPlanController {

    private final WeeklyPlanService weeklyPlanService;

    @GetMapping("/me")
    public WeeklyPlanResponse findMine(
            @CurrentMember Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate
    ) {
        return weeklyPlanService.findMine(memberId, weekStartDate);
    }

    @GetMapping("/members/{memberId}")
    public WeeklyPlanResponse findMemberPlan(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate
    ) {
        return weeklyPlanService.findForManager(currentMemberId, memberId, weekStartDate);
    }

    @PutMapping("/me")
    public WeeklyPlanResponse saveMine(@CurrentMember Long memberId, @RequestBody WeeklyPlanSaveRequest request) {
        return weeklyPlanService.saveMine(memberId, request);
    }

    @GetMapping("/monthly-goal/me")
    public MonthlyPlanGoalResponse findMonthlyGoal(@CurrentMember Long memberId, @RequestParam String month) {
        return weeklyPlanService.findMonthlyGoal(memberId, month);
    }

    @PutMapping("/monthly-goal/me")
    public MonthlyPlanGoalResponse saveMonthlyGoal(@CurrentMember Long memberId, @RequestBody MonthlyPlanGoalRequest request) {
        return weeklyPlanService.saveMonthlyGoal(memberId, request);
    }
}
