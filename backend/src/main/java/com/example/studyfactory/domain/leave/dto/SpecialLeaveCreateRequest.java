package com.example.studyfactory.domain.leave.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record SpecialLeaveCreateRequest(
        @NotNull(message = "사원은 필수입니다.")
        Long memberId,

        @NotEmpty(message = "휴무 날짜는 필수입니다.")
        List<LocalDate> leaveDates,

        @NotEmpty(message = "교시는 필수입니다.")
        List<Integer> slots,

        @NotNull(message = "사유는 필수입니다.")
        String reason,

        String customReason,

        boolean recurring
) {
}
