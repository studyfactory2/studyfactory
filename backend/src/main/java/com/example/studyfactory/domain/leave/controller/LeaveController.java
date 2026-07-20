package com.example.studyfactory.domain.leave.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.FixedLeaveGenerationResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveManagementResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MemberLeavePlanResponse;
import com.example.studyfactory.domain.leave.dto.MonthlyLeaveCalendarResponse;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveResponse;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.service.LeaveService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse create(@CurrentMember Long memberId, @Valid @RequestBody LeaveCreateRequest request) {
        return leaveService.create(memberId, request);
    }

    @GetMapping("/me")
    public List<LeaveResponse> findMine(@CurrentMember Long memberId) {
        return leaveService.findMine(memberId);
    }

    @GetMapping("/me/plan")
    public List<MemberLeavePlanResponse> findMyLeavePlan(@CurrentMember Long memberId) {
        return leaveService.findMyLeavePlan(memberId);
    }

    @DeleteMapping("/{leaveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMember Long memberId, @PathVariable Long leaveId) {
        leaveService.delete(memberId, leaveId);
    }

    @GetMapping("/daily-status")
    public List<DailyLeaveStatusResponse> findDailyStatuses(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) LeaveType leaveType
    ) {
        return leaveService.findDailyStatuses(date, name, branchId, leaveType);
    }

    @GetMapping("/monthly-calendar")
    public List<MonthlyLeaveCalendarResponse> findMonthlyCalendar(
            @CurrentMember Long currentMemberId,
            @RequestParam Long memberId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return leaveService.findMonthlyCalendar(currentMemberId, memberId, year, month);
    }

    @PostMapping("/special")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SpecialLeaveResponse> createSpecial(@CurrentMember Long memberId, @Valid @RequestBody SpecialLeaveCreateRequest request) {
        return leaveService.createSpecial(memberId, request);
    }

    @PostMapping("/fixed")
    @ResponseStatus(HttpStatus.CREATED)
    public FixedLeaveResponse createFixed(@CurrentMember Long memberId, @Valid @RequestBody FixedLeaveCreateRequest request) {
        return leaveService.createFixed(memberId, request);
    }

    @GetMapping("/fixed")
    public List<FixedLeaveManagementResponse> findFixedLeaves(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long branchId
    ) {
        return leaveService.findFixedLeaves(currentMemberId, name, branchId);
    }

    @DeleteMapping("/fixed/{fixedLeaveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFixed(@CurrentMember Long currentMemberId, @PathVariable Long fixedLeaveId) {
        leaveService.deleteFixed(currentMemberId, fixedLeaveId);
    }

    @PostMapping("/fixed/generate")
    public FixedLeaveGenerationResponse generateFixedLeaves(@CurrentMember Long currentMemberId) {
        return leaveService.generateFixedLeaves(currentMemberId);
    }

    @GetMapping("/special")
    public List<SpecialLeaveResponse> findSpecialByMember(@CurrentMember Long currentMemberId, @RequestParam Long memberId) {
        return leaveService.findSpecialByMember(currentMemberId, memberId);
    }

    @DeleteMapping("/special/{specialLeaveId}/slots/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpecialSlot(@CurrentMember Long currentMemberId, @PathVariable Long specialLeaveId, @PathVariable Integer slot) {
        leaveService.deleteSpecialSlot(currentMemberId, specialLeaveId, slot);
    }
}
