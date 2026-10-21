package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveOccurrenceCancellationRepository extends JpaRepository<LeaveOccurrenceCancellation, Long> {
    List<LeaveOccurrenceCancellation> findByMemberIdInAndLeaveDateBetween(
            Collection<Long> memberIds, LocalDate start, LocalDate end);
    List<LeaveOccurrenceCancellation> findByMemberIdAndLeaveDateBetween(Long memberId, LocalDate start, LocalDate end);
    List<LeaveOccurrenceCancellation> findByMemberIdAndBranchIdAndLeaveDateBetween(Long memberId, Long branchId, LocalDate start, LocalDate end);
    List<LeaveOccurrenceCancellation> findByBranchIdAndLeaveDateBetween(Long branchId, LocalDate start, LocalDate end);
    List<LeaveOccurrenceCancellation> findByLeaveDateBetween(LocalDate start, LocalDate end);
    void deleteByMemberId(Long memberId);
}
