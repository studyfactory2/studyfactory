package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceUnconfirmedSlot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceUnconfirmedSlotRepository extends JpaRepository<AttendanceUnconfirmedSlot, Long> {

    List<AttendanceUnconfirmedSlot> findByMemberIdAndBranchIdAndAttendanceDateGreaterThanEqualAndSlotIn(
            Long memberId, Long branchId, LocalDate startDate, List<Integer> slots
    );

    List<AttendanceUnconfirmedSlot> findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(
            Long branchId,
            LocalDate attendanceDate
    );

    boolean existsByMemberIdAndAttendanceDateAndSlot(Long memberId, LocalDate attendanceDate, int slot);

    List<AttendanceUnconfirmedSlot> findByMemberIdAndSlotIn(Long memberId, List<Integer> slots);

    void deleteByMemberIdAndAttendanceDateAndSlotIn(Long memberId, LocalDate attendanceDate, List<Integer> slots);

    void deleteByMemberIdAndAttendanceDateAndSlot(Long memberId, LocalDate attendanceDate, int slot);

    void deleteByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);

    void deleteByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("update AttendanceUnconfirmedSlot a set a.clearedByMemberId = null where a.clearedByMemberId = :memberId")
    void clearClearedByMemberId(@Param("memberId") Long memberId);
}
