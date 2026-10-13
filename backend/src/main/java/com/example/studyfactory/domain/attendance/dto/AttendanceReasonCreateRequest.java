package com.example.studyfactory.domain.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AttendanceReasonCreateRequest(
        @NotNull @Positive Long memberId,
        @NotNull @Size(min = 1, max = 31) List<@NotNull LocalDate> dates,
        @NotNull @Size(min = 1, max = 7) List<@NotNull @Min(1) @Max(7) Integer> slots,
        @NotBlank String reason
) {
}
