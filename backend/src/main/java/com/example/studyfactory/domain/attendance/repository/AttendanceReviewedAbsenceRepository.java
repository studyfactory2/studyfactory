package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceReviewedAbsenceRepository extends JpaRepository<AttendanceReviewedAbsence, Long> {

    List<AttendanceReviewedAbsence> findByBranchIdAndAttendanceDateOrderByMemberIdAscSlotAsc(
            Long branchId,
            LocalDate attendanceDate
    );

    void deleteByMemberIdAndAttendanceDateAndSlot(Long memberId, LocalDate attendanceDate, int slot);

    void deleteByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);

    void deleteByMemberId(Long memberId);

}
