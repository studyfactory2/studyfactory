package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedPlanItemRepository extends JpaRepository<SharedPlanItem, Long> {

    List<SharedPlanItem> findByMemberIdAndWeekStartDateOrderByDayIndexAscSortOrderAscIdAsc(
            Long memberId,
            LocalDate weekStartDate
    );

    Optional<SharedPlanItem> findByMemberIdAndClientRequestId(Long memberId, UUID clientRequestId);

    Optional<SharedPlanItem> findByIdAndMemberIdAndWeekStartDate(Long id, Long memberId, LocalDate weekStartDate);

    void deleteByMemberId(Long memberId);
}
