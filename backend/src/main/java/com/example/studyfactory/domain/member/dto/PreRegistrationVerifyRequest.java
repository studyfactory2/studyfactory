package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PreRegistrationVerifyRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotNull(message = "지점은 필수입니다.")
        Long branchId
) {
}
