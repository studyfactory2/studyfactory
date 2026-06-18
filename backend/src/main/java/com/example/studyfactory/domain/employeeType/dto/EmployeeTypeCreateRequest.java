package com.example.studyfactory.domain.employeeType.dto;

import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import jakarta.validation.constraints.NotBlank;

public record EmployeeTypeCreateRequest(
        @NotBlank(message = "사원구분 이름은 필수입니다.")
        String name
) {

    public EmployeeType toEntity() {
        return new EmployeeType(name.trim());
    }
}
