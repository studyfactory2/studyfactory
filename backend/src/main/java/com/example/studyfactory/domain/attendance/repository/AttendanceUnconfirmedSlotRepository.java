package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceUnconfirmedSlot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceUnconfirmedSlotRepository extends JpaRepository<AttendanceUnconfirmedSlot, Long> {

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
}
