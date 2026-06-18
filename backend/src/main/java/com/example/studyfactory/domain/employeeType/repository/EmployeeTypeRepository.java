package com.example.studyfactory.domain.employeeType.repository;

import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeTypeRepository extends JpaRepository<EmployeeType, Long> {

    boolean existsByName(String name);
}
