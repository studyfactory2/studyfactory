package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberSignupRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotNull(message = "지점은 필수입니다.")
        Long branchId,
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
