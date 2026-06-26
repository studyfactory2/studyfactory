package com.example.studyfactory.domain.todo.repository;

import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    List<TodoItem> findByBranchIdAndTodoDateOrderByCompletedAscPriorityDescCreatedAtAsc(Long branchId, LocalDate todoDate);

    boolean existsBySourceTypeAndSourceIdAndTodoDate(TodoSourceType sourceType, Long sourceId, LocalDate todoDate);

    void deleteByTargetMemberId(Long memberId);

    void deleteByCreatedByMemberId(Long memberId);
}
