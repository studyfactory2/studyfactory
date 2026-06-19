package com.example.studyfactory.domain.leave.controller;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.service.LeaveService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

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
