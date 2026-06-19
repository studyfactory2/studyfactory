package com.example.studyfactory.domain.staffSchedule.repository;

import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, Long> {
}
