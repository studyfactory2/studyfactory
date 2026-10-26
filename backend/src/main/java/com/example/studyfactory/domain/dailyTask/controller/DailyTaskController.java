package com.example.studyfactory.domain.dailyTask.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCalendarResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCreateRequest;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskDayResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskUpdateRequest;
import com.example.studyfactory.domain.dailyTask.service.DailyTaskService;
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
@RequestMapping("/api/member-tasks/me")
public class DailyTaskController {

    private final DailyTaskService dailyTaskService;

    @GetMapping
    public DailyTaskDayResponse findMine(@CurrentMember Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dailyTaskService.findMine(memberId, date);
    }

    @GetMapping("/calendar")
    public DailyTaskCalendarResponse findCalendarMine(@CurrentMember Long memberId, @RequestParam String month) {
        return dailyTaskService.findCalendarMine(memberId, month);
    }

    @PostMapping("/items")
    public DailyTaskDayResponse createMine(@CurrentMember Long memberId, @RequestBody DailyTaskCreateRequest request) {
        return dailyTaskService.createMine(memberId, request);
    }

    @PatchMapping("/items/{itemId}")
    public DailyTaskDayResponse updateMine(@CurrentMember Long memberId, @PathVariable Long itemId,
            @RequestBody DailyTaskUpdateRequest request) {
        return dailyTaskService.updateMine(memberId, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public DailyTaskDayResponse deleteMine(@CurrentMember Long memberId, @PathVariable Long itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long expectedRevision) {
        return dailyTaskService.deleteMine(memberId, itemId, date, expectedRevision);
    }
}
