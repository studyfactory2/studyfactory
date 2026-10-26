package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanWeek;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedPlanWeekRepository extends JpaRepository<SharedPlanWeek, Long> {

    Optional<SharedPlanWeek> findByMemberIdAndWeekStartDate(Long memberId, LocalDate weekStartDate);

    void deleteByMemberId(Long memberId);
}
