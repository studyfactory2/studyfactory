package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.MonthlyPlanGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyPlanGoalRepository extends JpaRepository<MonthlyPlanGoal, Long> {

    Optional<MonthlyPlanGoal> findByMemberIdAndMonth(Long memberId, String month);

    void deleteByMemberId(Long memberId);
}
