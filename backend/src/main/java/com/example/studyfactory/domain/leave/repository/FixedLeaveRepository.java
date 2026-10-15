package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.FixedLeave;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FixedLeaveRepository extends JpaRepository<FixedLeave, Long> {

    @Query("select distinct f.memberId from FixedLeave f order by f.memberId")
    List<Long> findRuleMemberIds();

    @Query("select f.memberId from FixedLeave f where f.id = :id")
    Optional<Long> findOwnerIdById(Long id);

    List<FixedLeave> findByMemberIdOrderByCreatedAtAsc(Long memberId);
    List<FixedLeave> findByMemberIdAndBranchIdOrderByCreatedAtAsc(Long memberId, Long branchId);
    List<FixedLeave> findByBranchIdOrderByCreatedAtAsc(Long branchId);
    List<FixedLeave> findAllByOrderByCreatedAtAsc();

    List<FixedLeave> findByMemberIdAndActiveTrueOrderByCreatedAtAsc(Long memberId);

    List<FixedLeave> findByMemberIdAndBranchIdAndActiveTrueOrderByCreatedAtAsc(Long memberId, Long branchId);

    List<FixedLeave> findByBranchIdAndActiveTrueOrderByCreatedAtAsc(Long branchId);

    List<FixedLeave> findByActiveTrueOrderByCreatedAtAsc();

    void deleteByMemberId(Long memberId);
}
