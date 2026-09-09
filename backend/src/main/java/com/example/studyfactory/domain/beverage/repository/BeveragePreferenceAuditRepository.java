package com.example.studyfactory.domain.beverage.repository;

import com.example.studyfactory.domain.beverage.entity.BeveragePreferenceAudit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeveragePreferenceAuditRepository extends JpaRepository<BeveragePreferenceAudit, Long> {

    Optional<BeveragePreferenceAudit> findByMemberId(Long memberId);

    List<BeveragePreferenceAudit> findByMemberIdIn(Collection<Long> memberIds);

    void deleteByMemberId(Long memberId);
}
