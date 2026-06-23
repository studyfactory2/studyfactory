package com.example.studyfactory.domain.certification.dto;

import com.example.studyfactory.domain.certification.entity.Certification;
import java.time.LocalDateTime;

public record CertificationResponse(
        Long id,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CertificationResponse from(Certification certification) {
        return new CertificationResponse(
                certification.getId(),
                certification.getContent(),
                certification.getCreatedAt(),
                certification.getUpdatedAt()
        );
    }
}
