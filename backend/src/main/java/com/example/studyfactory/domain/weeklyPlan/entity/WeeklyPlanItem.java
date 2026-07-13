package com.example.studyfactory.domain.weeklyPlan.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "weekly_plan_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyPlanItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "period_index", nullable = false)
    private int periodIndex;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public WeeklyPlanItem(
            Long memberId,
            Long branchId,
            LocalDate weekStartDate,
            int periodIndex,
            int dayIndex,
            String content,
            boolean done,
            int sortOrder
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.weekStartDate = weekStartDate;
        this.periodIndex = periodIndex;
        this.dayIndex = dayIndex;
        this.content = content;
        this.done = done;
        this.sortOrder = sortOrder;
    }
}
