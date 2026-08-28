package com.example.studyfactory.domain.studyPresence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudyPresenceQrRequest(
        @NotBlank(message = "QR 토큰은 필수입니다.")
        @Size(max = 128, message = "QR 토큰이 너무 깁니다.")
        String qrToken
) {
}
