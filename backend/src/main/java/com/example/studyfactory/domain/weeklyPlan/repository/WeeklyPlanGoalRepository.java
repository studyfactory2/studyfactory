package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPlanGoalRepository extends JpaRepository<WeeklyPlanGoal, Long> {

    Optional<WeeklyPlanGoal> findByMemberIdAndWeekStartDate(Long memberId, LocalDate weekStartDate);

    void deleteByMemberId(Long memberId);
}
