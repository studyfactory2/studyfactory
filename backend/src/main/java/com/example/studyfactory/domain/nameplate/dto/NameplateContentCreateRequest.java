package com.example.studyfactory.domain.nameplate.dto;

import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import jakarta.validation.constraints.NotBlank;

public record NameplateContentCreateRequest(
        @NotBlank(message = "명패내용은 필수입니다.")
        String content
) {

    public NameplateContent toEntity() {
        return new NameplateContent(content.trim());
    }
}
