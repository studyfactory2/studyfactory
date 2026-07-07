package com.example.studyfactory.domain.todo.repository;

import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    List<TodoItem> findByBranchIdAndTodoDateOrderByCompletedAscPriorityDescCreatedAtAsc(Long branchId, LocalDate todoDate);

    boolean existsBySourceTypeAndSourceIdAndTodoDate(TodoSourceType sourceType, Long sourceId, LocalDate todoDate);

    void deleteByTargetMemberId(Long memberId);

    void deleteByCreatedByMemberId(Long memberId);

    @Query("""
            select t.id
            from TodoItem t
            where t.completed = true
              and t.completedAt < :threshold
            """)
    List<Long> findCompletedIdsBefore(LocalDateTime threshold);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from TodoItem t
            where t.id in :todoItemIds
            """)
    void deleteByIdIn(List<Long> todoItemIds);
}
