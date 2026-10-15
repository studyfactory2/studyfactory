package com.example.studyfactory.domain.todo.repository;

import com.example.studyfactory.domain.todo.entity.TodoReply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoReplyRepository extends JpaRepository<TodoReply, Long> {

    List<TodoReply> findByTodoItemIdOrderByCreatedAtAsc(Long todoItemId);

    void deleteByTodoItemId(Long todoItemId);

    void deleteByTodoItemIdIn(List<Long> todoItemIds);

    void deleteByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from TodoReply r
            where r.todoItemId in (select t.id from TodoItem t where t.targetMemberId = :memberId)
            """)
    void deleteByTodoTargetMemberId(@Param("memberId") Long memberId);
}
