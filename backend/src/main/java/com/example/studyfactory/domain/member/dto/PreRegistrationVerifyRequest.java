package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PreRegistrationVerifyRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotNull(message = "지점은 필수입니다.")
        Long branchId,
        @Pattern(regexp = "\\d{8}", message = "등록 코드는 8자리 숫자여야 합니다.")
        String registrationCode
) {
    public PreRegistrationVerifyRequest(String name, Long branchId) {
        this(name, branchId, null);
    }
}
