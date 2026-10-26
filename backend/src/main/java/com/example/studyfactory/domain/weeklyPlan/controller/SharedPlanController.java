package com.example.studyfactory.domain.weeklyPlan.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanUpdateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanWeekResponse;
import com.example.studyfactory.domain.weeklyPlan.model.PlanItemSource;
import com.example.studyfactory.domain.weeklyPlan.service.SharedPlanService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weekly-plans/shared")
public class SharedPlanController {

    private final SharedPlanService sharedPlanService;

    @GetMapping("/me")
    public SharedPlanWeekResponse findMine(@CurrentMember Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return sharedPlanService.findMine(memberId, weekStartDate);
    }

    @GetMapping("/members/{memberId}")
    public SharedPlanWeekResponse findMemberPlan(@CurrentMember Long currentMemberId, @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return sharedPlanService.findForManager(currentMemberId, memberId, weekStartDate);
    }

    @PostMapping("/me/items")
    public SharedPlanWeekResponse createMine(@CurrentMember Long memberId, @RequestBody SharedPlanCreateRequest request) {
        return sharedPlanService.createMine(memberId, request);
    }

    @PatchMapping("/me/items/{source}/{itemId}")
    public SharedPlanWeekResponse updateMine(@CurrentMember Long memberId, @PathVariable PlanItemSource source,
            @PathVariable Long itemId, @RequestBody SharedPlanUpdateRequest request) {
        return sharedPlanService.updateMine(memberId, source, itemId, request);
    }

    @DeleteMapping("/me/items/{source}/{itemId}")
    public SharedPlanWeekResponse deleteMine(@CurrentMember Long memberId, @PathVariable PlanItemSource source,
            @PathVariable Long itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam Long expectedRevision) {
        return sharedPlanService.deleteMine(memberId, source, itemId, weekStartDate, expectedRevision);
    }
}
