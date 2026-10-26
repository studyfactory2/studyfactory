package com.example.studyfactory.domain.dailyTask.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "member_task_items",
        check = {
                @CheckConstraint(name = "ck_member_task_items_sort_order", constraint = "sort_order >= 0"),
                @CheckConstraint(
                        name = "ck_member_task_items_fingerprint",
                        constraint = "char_length(create_fingerprint) = 64"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_task_items_member_request",
                columnNames = {"member_id", "client_request_id"}
        ),
        indexes = @Index(
                name = "idx_member_task_items_member_date_order",
                columnList = "member_id, task_date, sort_order, id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyTaskItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "task_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;

    @Column(name = "create_fingerprint", nullable = false, length = 64)
    private String createFingerprint;

    @Column(nullable = false)
    private boolean deleted;

    public DailyTaskItem(
            Long memberId,
            LocalDate date,
            String content,
            int sortOrder,
            UUID clientRequestId,
            String createFingerprint
    ) {
        this.memberId = memberId;
        this.date = date;
        this.content = content;
        this.sortOrder = sortOrder;
        this.clientRequestId = clientRequestId;
        this.createFingerprint = createFingerprint;
    }

    public void update(String content, Boolean completed) {
        if (content != null) {
            this.content = content;
        }
        if (completed != null) {
            this.completed = completed;
        }
    }

    public void markDeleted() {
        deleted = true;
    }
}
