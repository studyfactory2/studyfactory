package com.example.studyfactory.member.repository;

import com.example.studyfactory.member.domain.EmployeeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeTypeRepository extends JpaRepository<EmployeeType, Long> {
}
