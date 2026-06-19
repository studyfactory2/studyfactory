package com.example.studyfactory.domain.suggestion.dto;

import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SuggestionCreateRequest(
        @NotNull(message = "건의사항 카테고리는 필수입니다.")
        SuggestionCategory category,

        @NotBlank(message = "건의사항 내용은 필수입니다.")
        String content
) {
}
