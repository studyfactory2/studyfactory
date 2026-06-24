package com.example.studyfactory.domain.staffSchedule.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleResponse;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleUpdateRequest;
import com.example.studyfactory.domain.staffSchedule.service.StaffScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/staff-schedules")
public class StaffScheduleController {

    private final StaffScheduleService staffScheduleService;

    @GetMapping
    public List<StaffScheduleResponse> findAll(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) Long branchId
    ) {
        return staffScheduleService.findAll(currentMemberId, branchId);
    }

    @PutMapping
    public List<StaffScheduleResponse> update(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) Long branchId,
            @Valid @RequestBody StaffScheduleUpdateRequest request
    ) {
        return staffScheduleService.update(currentMemberId, branchId, request);
    }
}
