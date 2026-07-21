package com.example.studyfactory.domain.beverage.repository;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeverageItemRepository extends JpaRepository<BeverageItem, Long> {

    List<BeverageItem> findByMemberIdOrderByCreatedAtAsc(Long memberId);

    boolean existsByMemberIdAndName(Long memberId, String name);

    long deleteByMemberIdAndName(Long memberId, String name);

    void deleteByMemberId(Long memberId);
}
