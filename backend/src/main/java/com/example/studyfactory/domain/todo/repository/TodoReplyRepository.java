package com.example.studyfactory.domain.todo.repository;

import com.example.studyfactory.domain.todo.entity.TodoReply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoReplyRepository extends JpaRepository<TodoReply, Long> {

    List<TodoReply> findByTodoItemIdOrderByCreatedAtAsc(Long todoItemId);

    void deleteByTodoItemId(Long todoItemId);

    void deleteByTodoItemIdIn(List<Long> todoItemIds);

    void deleteByMemberId(Long memberId);
}
