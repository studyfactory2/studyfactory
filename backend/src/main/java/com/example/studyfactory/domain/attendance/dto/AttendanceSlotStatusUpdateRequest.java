package com.example.studyfactory.domain.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AttendanceSlotStatusUpdateRequest(
        @NotNull Long memberId,
        @NotNull LocalDate date,
        @NotNull @Min(1) @Max(7) Integer slot,
        @NotNull AttendanceSlotStatusUpdateType status,
        String reason
) {
}
