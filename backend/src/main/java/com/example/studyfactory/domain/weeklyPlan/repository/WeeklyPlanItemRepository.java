package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPlanItemRepository extends JpaRepository<WeeklyPlanItem, Long> {

    List<WeeklyPlanItem> findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(Long memberId, LocalDate weekStartDate);

    Optional<WeeklyPlanItem> findByIdAndMemberIdAndWeekStartDate(Long id, Long memberId, LocalDate weekStartDate);

    void deleteByMemberIdAndWeekStartDate(Long memberId, LocalDate weekStartDate);

    void deleteByMemberId(Long memberId);
}
