package com.example.studyfactory.domain.certification.dto;

import com.example.studyfactory.domain.certification.entity.Certification;
import jakarta.validation.constraints.NotBlank;

public record CertificationCreateRequest(
        @NotBlank(message = "자격증은 필수입니다.")
        String content
) {

    public Certification toEntity() {
        return new Certification(content.trim());
    }
}
