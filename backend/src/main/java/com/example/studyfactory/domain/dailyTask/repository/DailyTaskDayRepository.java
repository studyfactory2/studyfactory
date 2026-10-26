package com.example.studyfactory.domain.dailyTask.repository;

import com.example.studyfactory.domain.dailyTask.entity.DailyTaskDay;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTaskDayRepository extends JpaRepository<DailyTaskDay, Long> {

    Optional<DailyTaskDay> findByMemberIdAndDate(Long memberId, LocalDate date);

    void deleteByMemberId(Long memberId);
}
