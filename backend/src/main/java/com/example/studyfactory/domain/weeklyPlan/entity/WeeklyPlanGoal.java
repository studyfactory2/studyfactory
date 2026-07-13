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
@Table(name = "weekly_plan_goals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyPlanGoal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false, columnDefinition = "text")
    private String goal;

    public WeeklyPlanGoal(Long memberId, Long branchId, LocalDate weekStartDate, String goal) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.weekStartDate = weekStartDate;
        this.goal = goal;
    }

    public void updateGoal(String goal) {
        this.goal = goal;
    }
}
