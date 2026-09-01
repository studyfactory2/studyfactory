package com.example.studyfactory.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record LoginRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,
        @Positive(message = "지점 ID는 양수여야 합니다.")
        Long branchId
) {
    public LoginRequest(String name, String password) {
        this(name, password, null);
    }
}
