package com.example.studyfactory.domain.sideDish.repository;

import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SideDishRequestRepository extends JpaRepository<SideDishRequest, Long> {

    @Query("""
            select s
            from SideDishRequest s
            where s.referenceInformation.memberId = :memberId
              and s.mealInformation.mealDate = :mealDate
            order by s.createdAt desc
            """)
    List<SideDishRequest> findMineByDate(Long memberId, LocalDate mealDate);
}
