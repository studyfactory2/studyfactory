package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialLeaveRepository extends JpaRepository<SpecialLeave, Long> {
}
