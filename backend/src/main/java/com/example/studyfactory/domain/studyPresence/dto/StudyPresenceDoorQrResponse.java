package com.example.studyfactory.domain.studyPresence.dto;

public record StudyPresenceDoorQrResponse(
        Long branchId,
        String qrToken
) {
}
