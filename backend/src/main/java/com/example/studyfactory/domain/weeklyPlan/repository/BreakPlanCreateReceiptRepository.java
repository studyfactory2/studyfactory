package com.example.studyfactory.domain.weeklyPlan.repository;

import com.example.studyfactory.domain.weeklyPlan.entity.BreakPlanCreateReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreakPlanCreateReceiptRepository extends JpaRepository<BreakPlanCreateReceipt, Long> {

    Optional<BreakPlanCreateReceipt> findByMemberIdAndClientRequestId(Long memberId, UUID clientRequestId);

    void deleteByMemberId(Long memberId);
}
