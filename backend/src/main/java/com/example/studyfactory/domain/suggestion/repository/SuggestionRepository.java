package com.example.studyfactory.domain.suggestion.repository;

import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    @Query("""
            select s
            from Suggestion s
            where s.referenceInformation.memberId = :memberId
            order by s.createdAt desc
            """)
    List<Suggestion> findMine(Long memberId);

    List<Suggestion> findAllByOrderByCreatedAtDesc();

    @Query("""
            select s
            from Suggestion s
            where s.referenceInformation.branchId = :branchId
            order by s.createdAt desc
            """)
    List<Suggestion> findByBranchId(Long branchId);

    void deleteByReferenceInformationMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Suggestion s
            set s.referenceInformation.resolvedByMemberId = null
            where s.referenceInformation.resolvedByMemberId = :memberId
            """)
    void clearResolvedByMemberId(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Suggestion s
            where s.isResolved = true
              and (
                  s.resolvedAt < :threshold
                  or (s.resolvedAt is null and s.updatedAt < :threshold)
              )
            """)
    int deleteResolvedBefore(LocalDateTime threshold);
}
