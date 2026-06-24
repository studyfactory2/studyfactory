package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialLeaveRepository extends JpaRepository<SpecialLeave, Long> {

    List<SpecialLeave> findByMemberIdOrderByLeaveDateDescCreatedAtDesc(Long memberId);

    List<SpecialLeave> findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
            Long memberId, LocalDate startDate, LocalDate endDate
    );

    List<SpecialLeave> findByBranchIdAndLeaveDateOrderByCreatedAtAsc(Long branchId, LocalDate leaveDate);
}
