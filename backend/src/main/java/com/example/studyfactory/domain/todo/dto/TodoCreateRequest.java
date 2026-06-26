package com.example.studyfactory.domain.todo.dto;

import com.example.studyfactory.domain.todo.entity.TodoPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TodoCreateRequest(
        @NotNull Long branchId,
        @NotNull LocalDate todoDate,
        @NotBlank String content,
        @NotNull TodoPriority priority
) {
}
