package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.FixedLeave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedLeaveRepository extends JpaRepository<FixedLeave, Long> {
}
