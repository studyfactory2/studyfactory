package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceDailyInitialization;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceDailyInitializationRepository extends JpaRepository<AttendanceDailyInitialization, Long> {

    List<AttendanceDailyInitialization> findByBranchIdAndAttendanceDate(Long branchId, LocalDate attendanceDate);

    boolean existsByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);

    void deleteByMemberId(Long memberId);

    void deleteByInitializedByMemberId(Long initializedByMemberId);
}
