package com.example.studyfactory.domain.todo.dto;

import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoPriority;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TodoResponse(
        Long id,
        Long branchId,
        LocalDate todoDate,
        String content,
        TodoPriority priority,
        TodoSourceType sourceType,
        Long sourceId,
        Long targetMemberId,
        Long createdByMemberId,
        String createdByMemberName,
        boolean completed,
        Long completedByMemberId,
        LocalDateTime completedAt,
        List<TodoReplyResponse> replies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TodoResponse from(TodoItem todoItem, List<TodoReplyResponse> replies) {
        return new TodoResponse(
                todoItem.getId(),
                todoItem.getBranchId(),
                todoItem.getTodoDate(),
                todoItem.getContent(),
                todoItem.getPriority(),
                todoItem.getSourceType(),
                todoItem.getSourceId(),
                todoItem.getTargetMemberId(),
                todoItem.getCreatedByMemberId(),
                todoItem.getCreatedByMemberName(),
                todoItem.isCompleted(),
                todoItem.getCompletedByMemberId(),
                todoItem.getCompletedAt(),
                replies,
                todoItem.getCreatedAt(),
                todoItem.getUpdatedAt()
        );
    }
}
