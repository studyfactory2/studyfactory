package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceReviewedAbsenceRepository extends JpaRepository<AttendanceReviewedAbsence, Long> {

    List<AttendanceReviewedAbsence> findByMemberIdAndBranchIdAndAttendanceDateGreaterThanEqualAndSlotIn(
            Long memberId, Long branchId, LocalDate startDate, List<Integer> slots
    );

    boolean existsByMemberIdAndAttendanceDateAndSlot(Long memberId, LocalDate attendanceDate, int slot);

    List<AttendanceReviewedAbsence> findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(
            Long branchId,
            LocalDate attendanceDate
    );

    void deleteByMemberIdAndAttendanceDateAndSlot(Long memberId, LocalDate attendanceDate, int slot);

    void deleteByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);

    void deleteByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("update AttendanceReviewedAbsence a set a.reviewedByMemberId = null where a.reviewedByMemberId = :memberId")
    void clearReviewedByMemberId(@Param("memberId") Long memberId);

}
