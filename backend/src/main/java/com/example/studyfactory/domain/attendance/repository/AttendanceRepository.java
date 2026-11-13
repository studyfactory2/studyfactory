package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** Only current O overrides, using the same precedence as the attendance board. */
    @Query("""
            select a from Attendance a
            where a.referenceInformation.memberId in :memberIds
              and a.slotInformation.attendanceDate between :fromDate and :toDate
              and (a.slotInformation.customStatusText is null
                   or a.slotInformation.customStatusText <> :cancellationMarker)
              and not exists (
                  select r.id from AttendanceReviewedAbsence r
                  where r.memberId = a.referenceInformation.memberId
                    and r.branchId = a.referenceInformation.branchId
                    and r.attendanceDate = a.slotInformation.attendanceDate
                    and r.slot = a.slotInformation.slot
              )
              and not exists (
                  select u.id from AttendanceUnconfirmedSlot u
                  where u.memberId = a.referenceInformation.memberId
                    and u.branchId = a.referenceInformation.branchId
                    and u.attendanceDate = a.slotInformation.attendanceDate
                    and u.slot = a.slotInformation.slot
              )
            """)
    List<Attendance> findPresentOverridesForMembers(
            @Param("memberIds") Collection<Long> memberIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("cancellationMarker") String cancellationMarker
    );

    @Query("""
            select a from Attendance a
            where a.referenceInformation.memberId = :memberId
              and a.referenceInformation.branchId = :branchId
              and a.slotInformation.attendanceDate >= :startDate
              and a.slotInformation.slot in :slots
            """)
    List<Attendance> findFutureSelectedSlots(
            @Param("memberId") Long memberId,
            @Param("branchId") Long branchId,
            @Param("startDate") LocalDate startDate,
            @Param("slots") List<Integer> slots
    );

    List<Attendance> findByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(
            Long memberId, LocalDate attendanceDate, int slot
    );

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

    @Modifying(flushAutomatically = true)
    @Query("""
            update Attendance a
            set a.referenceInformation.markedByMemberId = null
            where a.referenceInformation.markedByMemberId = :memberId
            """)
    void clearMarkedByMemberId(@Param("memberId") Long memberId);
}
