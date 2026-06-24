package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.FixedLeave;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedLeaveRepository extends JpaRepository<FixedLeave, Long> {

    List<FixedLeave> findByMemberIdAndActiveTrueOrderByCreatedAtAsc(Long memberId);

    List<FixedLeave> findByBranchIdAndActiveTrueOrderByCreatedAtAsc(Long branchId);
}
