package com.example.studyfactory.domain.suggestion.dto;

import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import java.time.LocalDateTime;

public record SuggestionResponse(
        Long id,
        Long memberId,
        String memberName,
        Long branchId,
        Long resolvedByMemberId,
        String resolvedByMemberName,
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
                null,
                suggestion.getBranchId(),
                suggestion.getResolvedByMemberId(),
                null,
                suggestion.getCategory(),
                suggestion.getContent(),
                suggestion.isResolved(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt()
        );
    }

    public static SuggestionResponse from(Suggestion suggestion, String memberName, String resolvedByMemberName) {
        return new SuggestionResponse(
                suggestion.getId(),
                suggestion.getMemberId(),
                memberName,
                suggestion.getBranchId(),
                suggestion.getResolvedByMemberId(),
                resolvedByMemberName,
                suggestion.getCategory(),
                suggestion.getContent(),
                suggestion.isResolved(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt()
        );
    }
}
