package com.example.studyfactory.domain.nameplate.dto;

import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import java.time.LocalDateTime;

public record NameplateContentResponse(
        Long id,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NameplateContentResponse from(NameplateContent nameplateContent) {
        return new NameplateContentResponse(
                nameplateContent.getId(),
                nameplateContent.getContent(),
                nameplateContent.getCreatedAt(),
                nameplateContent.getUpdatedAt()
        );
    }
}
