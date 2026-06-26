package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByMemberIdOrderByLeaveDateDescCreatedAtDesc(Long memberId);

    List<LeaveRequest> findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
            Long memberId, LocalDate startDate, LocalDate endDate
    );

    List<LeaveRequest> findByBranchIdAndLeaveDateOrderByCreatedAtAsc(Long branchId, LocalDate leaveDate);

    List<LeaveRequest> findByMemberIdAndLeaveDateOrderByCreatedAtAsc(Long memberId, LocalDate leaveDate);

    void deleteByMemberId(Long memberId);

    @Query("""
            select new com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse(
                m.id,
                b.id,
                m.workInformation.seatNumber,
                m.name,
                b.name,
                l.leaveType,
                l.createdAt
            )
            from LeaveRequest l
            join Member m on m.id = l.memberId
            join Branch b on b.id = l.branchId
            where l.leaveDate = :date
              and (:name is null or m.name like concat('%', cast(:name as string), '%'))
              and (:branchId is null or l.branchId = :branchId)
              and (:leaveType is null or l.leaveType = :leaveType)
            order by l.createdAt asc, m.name asc
            """)
    List<DailyLeaveStatusResponse> findDailyStatuses(
            @Param("date") LocalDate date, @Param("name") String name,
            @Param("branchId") Long branchId, @Param("leaveType") LeaveType leaveType
    );
}
