package com.example.studyfactory.domain.suggestion.repository;

import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    @Query("""
            select s
            from Suggestion s
            where s.referenceInformation.memberId = :memberId
            order by s.createdAt desc
            """)
    List<Suggestion> findMine(Long memberId);
}
