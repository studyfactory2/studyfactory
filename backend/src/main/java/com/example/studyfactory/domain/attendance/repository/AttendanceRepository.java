package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("""
            select a
            from Attendance a
            where a.referenceInformation.branchId = :branchId
              and a.slotInformation.attendanceDate = :attendanceDate
            order by a.createdAt asc
            """)
    List<Attendance> findDailyBoardAttendances(
            @Param("branchId") Long branchId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    @Query("""
            select a
            from Attendance a
            where a.referenceInformation.memberId = :memberId
              and a.slotInformation.attendanceDate between :fromDate and :toDate
            order by a.slotInformation.attendanceDate asc,
                     a.slotInformation.slot asc,
                     a.createdAt asc,
                     a.id asc
            """)
    List<Attendance> findByMemberIdAndAttendanceDateBetween(
            @Param("memberId") Long memberId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select a
            from Attendance a
            where a.referenceInformation.memberId = :memberId
              and a.referenceInformation.branchId = :branchId
              and a.slotInformation.attendanceDate between :fromDate and :toDate
            order by a.slotInformation.attendanceDate asc,
                     a.slotInformation.slot asc,
                     a.createdAt asc,
                     a.id asc
            """)
    List<Attendance> findByMemberIdAndBranchIdAndAttendanceDateBetween(
            @Param("memberId") Long memberId,
            @Param("branchId") Long branchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    void deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(
            Long memberId, LocalDate attendanceDate, int slot
    );

    void deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDate(Long memberId, LocalDate attendanceDate);

    void deleteByReferenceInformationMemberId(Long memberId);
}
