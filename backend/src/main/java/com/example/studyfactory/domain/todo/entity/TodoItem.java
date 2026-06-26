package com.example.studyfactory.domain.todo.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "todo_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private LocalDate todoDate;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TodoSourceType sourceType;

    private Long sourceId;

    private Long targetMemberId;

    @Column(name = "created_by_member_id")
    private Long createdByMemberId;

    private String createdByMemberName;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_by_member_id")
    private Long completedByMemberId;

    private LocalDateTime completedAt;

    public TodoItem(
            Long branchId,
            LocalDate todoDate,
            String content,
            TodoPriority priority,
            TodoSourceType sourceType,
            Long sourceId,
            Long targetMemberId,
            Long createdByMemberId,
            String createdByMemberName
    ) {
        this.branchId = branchId;
        this.todoDate = todoDate;
        this.content = content;
        this.priority = priority;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetMemberId = targetMemberId;
        this.createdByMemberId = createdByMemberId;
        this.createdByMemberName = createdByMemberName;
        this.completed = false;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateCompletion(boolean completed, Long completedByMemberId) {
        this.completed = completed;
        if (completed) {
            this.completedByMemberId = completedByMemberId;
            this.completedAt = LocalDateTime.now();
            return;
        }

        this.completedByMemberId = null;
        this.completedAt = null;
    }

}
