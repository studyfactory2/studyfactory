package com.example.studyfactory.domain.employeeType.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사원구분 도메인 테스트")
class EmployeeTypeTest {

    @Test
    @DisplayName("사원구분 생성 요청 DTO로 사원구분 엔티티를 생성한다")
    void createEmployeeTypeFromRequest() {
        EmployeeTypeCreateRequest request = new EmployeeTypeCreateRequest(" 정규직 ");

        EmployeeType employeeType = request.toEntity();

        assertThat(employeeType.getName()).isEqualTo("정규직");
    }
}
