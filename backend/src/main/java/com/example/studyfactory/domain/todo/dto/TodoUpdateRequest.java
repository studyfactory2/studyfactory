package com.example.studyfactory.domain.todo.dto;

import jakarta.validation.constraints.NotBlank;

public record TodoUpdateRequest(@NotBlank String content) {
}
