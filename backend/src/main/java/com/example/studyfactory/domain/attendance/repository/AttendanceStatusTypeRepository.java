package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceStatusTypeRepository extends JpaRepository<AttendanceStatusType, Long> {

    Optional<AttendanceStatusType> findByName(String name);
}
