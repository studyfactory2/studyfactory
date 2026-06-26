package com.example.studyfactory.domain.attendance.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AttendanceDailyResetRequest(
        @NotNull Long memberId,
        @NotNull LocalDate date
) {
}
