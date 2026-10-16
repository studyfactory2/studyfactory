package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Cancels one displayed source occurrence, never all leave at the same attendance cell. */
public record LeaveOccurrenceCancellationRequest(
        @NotNull @Min(1) Long memberId,
        @NotNull @Min(1) Long branchId,
        @NotNull LocalDate leaveDate,
        @NotNull @Min(1) @Max(7) Integer slot,
        @NotNull LeaveSourceType sourceType,
        @NotNull @Min(1) Long sourceId
) {}
