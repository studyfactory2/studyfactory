package com.example.studyfactory.domain.suggestion.dto;

import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import java.time.LocalDateTime;

public record SuggestionResponse(
        Long id,
        Long memberId,
        Long branchId,
        Long resolvedByMemberId,
        SuggestionCategory category,
        String content,
        boolean isResolved,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SuggestionResponse from(Suggestion suggestion) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getMemberId(),
                suggestion.getBranchId(),
                suggestion.getResolvedByMemberId(),
                suggestion.getCategory(),
                suggestion.getContent(),
                suggestion.isResolved(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt()
        );
    }
}
