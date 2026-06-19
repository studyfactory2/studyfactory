package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceStatusTypeRepository extends JpaRepository<AttendanceStatusType, Long> {
}
