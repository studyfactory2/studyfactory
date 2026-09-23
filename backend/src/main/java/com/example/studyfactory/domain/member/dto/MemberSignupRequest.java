package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MemberSignupRequest(
        @NotNull(message = "사원 정보는 필수입니다.")
        Long memberId,
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{4}", message = "비밀번호는 4자리 숫자여야 합니다.")
        String password,
        @Pattern(regexp = "\\d{8}", message = "등록 코드는 8자리 숫자여야 합니다.")
        String registrationCode
) {
    public MemberSignupRequest(Long memberId, String password) {
        this(memberId, password, null);
    }
}
