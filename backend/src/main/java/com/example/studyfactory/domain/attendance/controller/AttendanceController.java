package com.example.studyfactory.domain.attendance.controller;

import com.example.studyfactory.domain.attendance.dto.AttendanceDailyResetRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.DailyAttendanceBoardResponse;
import com.example.studyfactory.domain.attendance.service.AttendanceService;
import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendances")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/daily-board")
    public DailyAttendanceBoardResponse findDailyBoard(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false) Long branchId
    ) {
        return attendanceService.findDailyBoard(currentMemberId, date, branchId);
    }

    @PatchMapping("/daily-board/slot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSlotStatus(
            @CurrentMember Long currentMemberId,
            @Valid @RequestBody AttendanceSlotStatusUpdateRequest request
    ) {
        attendanceService.updateSlotStatus(currentMemberId, request);
    }

    @PatchMapping("/daily-board/member/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetDailyStatus(
            @CurrentMember Long currentMemberId,
            @Valid @RequestBody AttendanceDailyResetRequest request
    ) {
        attendanceService.resetDailyStatus(currentMemberId, request);
    }
}
