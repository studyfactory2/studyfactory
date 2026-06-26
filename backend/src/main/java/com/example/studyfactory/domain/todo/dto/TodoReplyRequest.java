package com.example.studyfactory.domain.todo.dto;

import jakarta.validation.constraints.NotBlank;

public record TodoReplyRequest(@NotBlank String replyContent) {
}
