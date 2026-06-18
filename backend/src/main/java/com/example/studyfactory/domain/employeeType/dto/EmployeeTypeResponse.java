package com.example.studyfactory.domain.employeeType.dto;

import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import java.time.LocalDateTime;

public record EmployeeTypeResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EmployeeTypeResponse from(EmployeeType employeeType) {
        return new EmployeeTypeResponse(
                employeeType.getId(),
                employeeType.getName(),
                employeeType.getCreatedAt(),
                employeeType.getUpdatedAt()
        );
    }
}
