package com.example.studyfactory.domain.weeklyPlan.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "monthly_plan_goals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyPlanGoal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "plan_month", nullable = false, length = 7)
    private String month;

    @Column(nullable = false, columnDefinition = "text")
    private String goal;

    public MonthlyPlanGoal(Long memberId, Long branchId, YearMonth month, String goal) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.month = month.toString();
        this.goal = goal;
    }

    public void updateGoal(String goal) {
        this.goal = goal;
    }
}
