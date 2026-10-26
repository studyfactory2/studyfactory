package com.example.studyfactory.domain.dailyTask.repository;

import com.example.studyfactory.domain.dailyTask.entity.DailyTaskItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyTaskItemRepository extends JpaRepository<DailyTaskItem, Long> {

    Optional<DailyTaskItem> findByMemberIdAndClientRequestId(Long memberId, UUID clientRequestId);

    Optional<DailyTaskItem> findByIdAndMemberIdAndDate(Long id, Long memberId, LocalDate date);

    List<DailyTaskItem> findByMemberIdAndDateAndDeletedFalseOrderBySortOrderAscIdAsc(Long memberId, LocalDate date);

    @Query("""
            select max(item.sortOrder) from DailyTaskItem item
            where item.memberId = :memberId and item.date = :date
            """)
    Integer findMaxSortOrder(@Param("memberId") Long memberId, @Param("date") LocalDate date);

    @Query("""
            select item.date as date, count(item) as totalCount,
                   sum(case when item.completed = true then 1 else 0 end) as completedCount
            from DailyTaskItem item
            where item.memberId = :memberId and item.date >= :startDate
                  and item.date < :endDateExclusive and item.deleted = false
            group by item.date
            order by item.date
            """)
    List<DailyTaskDateCount> calendarCounts(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDateExclusive") LocalDate endDateExclusive
    );

    void deleteByMemberId(Long memberId);

    interface DailyTaskDateCount {
        LocalDate getDate();

        long getTotalCount();

        long getCompletedCount();
    }
}
