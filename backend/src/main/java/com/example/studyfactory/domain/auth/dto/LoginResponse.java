package com.example.studyfactory.domain.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken
) {
}
