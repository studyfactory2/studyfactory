package com.example.studyfactory.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record FixedLeaveCreateRequest(
        @NotNull(message = "사원은 필수입니다.")
        Long memberId,

        @NotNull(message = "기준 날짜는 필수입니다.")
        LocalDate leaveDate,

        @NotEmpty(message = "교시는 필수입니다.")
        List<@Min(1) @Max(7) Integer> slots,

        @NotNull(message = "사유는 필수입니다.")
        String reason
) {
}
