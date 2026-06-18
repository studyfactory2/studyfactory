package com.example.studyfactory.domain.employeeType.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("사원구분 예외 테스트")
class EmployeeTypeExceptionTest {

    @Test
    @DisplayName("중복된 사원구분 이름 예외를 생성한다")
    void createDuplicatedNameException() {
        EmployeeTypeException exception = EmployeeTypeException.duplicatedName();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 등록된 사원구분입니다.");
    }
}
