package com.example.studyfactory.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AccessTokenReissueRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
