package com.example.studyfactory.domain.leave.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
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
}
