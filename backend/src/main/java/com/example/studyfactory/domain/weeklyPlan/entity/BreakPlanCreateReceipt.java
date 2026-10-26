package com.example.studyfactory.domain.weeklyPlan.entity;

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
        name = "break_plan_create_receipts",
        check = @CheckConstraint(
                name = "ck_break_plan_create_receipts_fingerprint",
                constraint = "char_length(create_fingerprint) = 64"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_break_plan_create_receipts_member_request",
                columnNames = {"member_id", "client_request_id"}
        ),
        indexes = @Index(
                name = "idx_break_plan_create_receipts_member_week",
                columnList = "member_id, week_start_date"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreakPlanCreateReceipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "week_start_date", nullable = false, updatable = false)
    private LocalDate weekStartDate;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(name = "create_fingerprint", nullable = false, updatable = false, length = 64)
    private String createFingerprint;

    // Deliberately not a relationship: keep the receipt if the original item is deleted.
    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    public BreakPlanCreateReceipt(
            Long memberId,
            LocalDate weekStartDate,
            UUID clientRequestId,
            String createFingerprint,
            Long itemId
    ) {
        this.memberId = memberId;
        this.weekStartDate = weekStartDate;
        this.clientRequestId = clientRequestId;
        this.createFingerprint = createFingerprint;
        this.itemId = itemId;
    }
}
