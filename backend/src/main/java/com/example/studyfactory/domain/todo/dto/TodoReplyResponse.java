package com.example.studyfactory.domain.todo.dto;

import com.example.studyfactory.domain.todo.entity.TodoReply;
import java.time.LocalDateTime;

public record TodoReplyResponse(
        Long id,
        Long todoItemId,
        Long memberId,
        String memberName,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TodoReplyResponse from(TodoReply todoReply) {
        return new TodoReplyResponse(
                todoReply.getId(),
                todoReply.getTodoItemId(),
                todoReply.getMemberId(),
                todoReply.getMemberName(),
                todoReply.getContent(),
                todoReply.getCreatedAt(),
                todoReply.getUpdatedAt()
        );
    }
}
