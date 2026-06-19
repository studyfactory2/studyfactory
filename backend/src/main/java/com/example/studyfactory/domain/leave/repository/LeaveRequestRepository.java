package com.example.studyfactory.domain.leave.repository;

import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
}
