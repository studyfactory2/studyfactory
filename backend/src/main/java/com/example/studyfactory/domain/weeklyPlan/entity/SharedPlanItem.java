package com.example.studyfactory.domain.weeklyPlan.entity;

import com.example.studyfactory.common.BaseEntity;
import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "shared_plan_items",
        check = {
                @CheckConstraint(name = "ck_shared_plan_items_day", constraint = "day_index between 0 and 6"),
                @CheckConstraint(name = "ck_shared_plan_items_sort_order", constraint = "sort_order >= 0"),
                @CheckConstraint(
                        name = "ck_shared_plan_items_group",
                        constraint = "plan_group in ('PERIODS_1_2', 'PERIODS_3_5', 'PERIODS_6_7')"
                ),
                @CheckConstraint(
                        name = "ck_shared_plan_items_fingerprint",
                        constraint = "char_length(create_fingerprint) = 64"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_plan_items_member_request",
                columnNames = {"member_id", "client_request_id"}
        ),
        indexes = @Index(
                name = "idx_shared_plan_items_member_week_order",
                columnList = "member_id, week_start_date, day_index, sort_order, id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedPlanItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "plan_group", nullable = false, length = 30)
    private PlanGroup group;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;

    @Column(name = "create_fingerprint", nullable = false, length = 64)
    private String createFingerprint;

    @Column(nullable = false)
    private boolean deleted;

    public SharedPlanItem(
            Long memberId,
            Long branchId,
            LocalDate weekStartDate,
            int dayIndex,
            PlanGroup group,
            String content,
            int sortOrder,
            UUID clientRequestId,
            String createFingerprint
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.weekStartDate = weekStartDate;
        this.dayIndex = dayIndex;
        this.group = group;
        this.content = content;
        this.sortOrder = sortOrder;
        this.clientRequestId = clientRequestId;
        this.createFingerprint = createFingerprint;
    }

    public void update(String content, Boolean done) {
        if (content != null) {
            this.content = content;
        }
        if (done != null) {
            this.done = done;
        }
    }

    public void markDeleted() {
        deleted = true;
    }
}
