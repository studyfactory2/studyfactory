package com.example.studyfactory.domain.member.dto;

import java.time.LocalDateTime;

public record RegistrationCodeIssueResponse(
        String registrationCode,
        LocalDateTime registrationCodeExpiresAt
) {
}
