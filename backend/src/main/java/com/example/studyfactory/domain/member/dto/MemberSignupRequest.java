package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberSignupRequest(
        @NotNull(message = "사전등록 정보는 필수입니다.")
        Long preRegistrationId,
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
