package com.example.studyfactory.domain.beverage.repository;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeveragePreferenceRepository extends JpaRepository<BeveragePreference, Long> {

    Optional<BeveragePreference> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<BeveragePreference> findByMemberId(Long memberId);
}
