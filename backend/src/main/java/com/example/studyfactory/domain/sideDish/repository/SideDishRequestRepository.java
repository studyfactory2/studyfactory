package com.example.studyfactory.domain.sideDish.repository;

import com.example.studyfactory.domain.sideDish.dto.DailySideDishResponse;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SideDishRequestRepository extends JpaRepository<SideDishRequest, Long> {

    @Query("""
            select s
            from SideDishRequest s
            where s.referenceInformation.memberId = :memberId
              and s.mealInformation.mealDate = :mealDate
            order by s.createdAt desc
            """)
    List<SideDishRequest> findMineByDate(Long memberId, LocalDate mealDate);

    @Query("""
            select distinct s.mealInformation.mealDate
            from SideDishRequest s
            where s.referenceInformation.memberId = :memberId
              and s.mealInformation.mealDate between :from and :to
            order by s.mealInformation.mealDate
            """)
    List<LocalDate> findMineOrderDates(@Param("memberId") Long memberId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select new com.example.studyfactory.domain.sideDish.dto.DailySideDishResponse(
                s.id,
                s.referenceInformation.memberId,
                s.referenceInformation.branchId,
                m.name,
                m.workInformation.seatNumber,
                s.mealInformation.mealDate,
                s.mealInformation.mealType,
                s.orderInformation.items,
                s.orderInformation.totalPrice
            )
            from SideDishRequest s
            join Member m on m.id = s.referenceInformation.memberId
            where s.referenceInformation.branchId = :branchId
              and s.mealInformation.mealDate = :mealDate
            order by s.mealInformation.mealType asc, m.workInformation.seatNumber asc, s.createdAt asc
            """)
    List<DailySideDishResponse> findDailyByBranchAndDate(@Param("branchId") Long branchId, @Param("mealDate") LocalDate mealDate);

    void deleteByReferenceInformationMemberId(Long memberId);
}
