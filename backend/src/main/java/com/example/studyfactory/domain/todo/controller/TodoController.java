package com.example.studyfactory.domain.todo.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.todo.dto.TodoCompletionRequest;
import com.example.studyfactory.domain.todo.dto.TodoCreateRequest;
import com.example.studyfactory.domain.todo.dto.TodoReplyRequest;
import com.example.studyfactory.domain.todo.dto.TodoResponse;
import com.example.studyfactory.domain.todo.dto.TodoUpdateRequest;
import com.example.studyfactory.domain.todo.service.TodoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    @GetMapping("/daily")
    public List<TodoResponse> findDaily(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return todoService.findDailyWithJoinTodos(currentMemberId, branchId, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@CurrentMember Long currentMemberId, @Valid @RequestBody TodoCreateRequest request) {
        return todoService.create(currentMemberId, request);
    }

    @PatchMapping("/{todoId}/completion")
    public TodoResponse updateCompletion(
            @CurrentMember Long currentMemberId,
            @PathVariable Long todoId,
            @Valid @RequestBody TodoCompletionRequest request
    ) {
        return todoService.updateCompletion(currentMemberId, todoId, request);
    }

    @PatchMapping("/{todoId}")
    public TodoResponse update(
            @CurrentMember Long currentMemberId,
            @PathVariable Long todoId,
            @Valid @RequestBody TodoUpdateRequest request
    ) {
        return todoService.update(currentMemberId, todoId, request);
    }

    @PatchMapping("/{todoId}/reply")
    public TodoResponse updateReply(
            @CurrentMember Long currentMemberId,
            @PathVariable Long todoId,
            @Valid @RequestBody TodoReplyRequest request
    ) {
        return todoService.updateReply(currentMemberId, todoId, request);
    }

    @DeleteMapping("/{todoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMember Long currentMemberId, @PathVariable Long todoId) {
        todoService.delete(currentMemberId, todoId);
    }
}
