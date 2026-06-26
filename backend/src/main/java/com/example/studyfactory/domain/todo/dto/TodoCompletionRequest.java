package com.example.studyfactory.domain.todo.dto;

import jakarta.validation.constraints.NotNull;

public record TodoCompletionRequest(
        @NotNull Boolean completed
) {
}
