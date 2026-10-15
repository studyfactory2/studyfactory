package com.example.studyfactory.domain.todo.repository;

import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TodoItem t where t.id = :todoId")
    Optional<TodoItem> findByIdForUpdate(@Param("todoId") Long todoId);

    List<TodoItem> findByBranchIdAndTodoDateOrderByCompletedAscPriorityDescCreatedAtAsc(Long branchId, LocalDate todoDate);

    boolean existsBySourceTypeAndSourceIdAndTodoDate(TodoSourceType sourceType, Long sourceId, LocalDate todoDate);

    Optional<TodoItem> findBySourceTypeAndSourceIdAndTodoDate(TodoSourceType sourceType, Long sourceId, LocalDate todoDate);

    void deleteByTargetMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update TodoItem t
            set t.createdByMemberId = null, t.createdByMemberName = null
            where t.createdByMemberId = :memberId
            """)
    void clearCreatedByMember(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("update TodoItem t set t.completedByMemberId = null where t.completedByMemberId = :memberId")
    void clearCompletedByMemberId(@Param("memberId") Long memberId);

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
