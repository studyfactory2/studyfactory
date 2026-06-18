package com.example.studyfactory.domain.preRegistration.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("사전등록 예외 테스트")
class PreRegistrationExceptionTest {

    @Test
    @DisplayName("존재하지 않는 지점 예외를 생성한다")
    void createInvalidBranchException() {
        PreRegistrationException exception = PreRegistrationException.invalidBranch();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("존재하지 않는 지점입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 사원구분 예외를 생성한다")
    void createInvalidEmployeeTypeException() {
        PreRegistrationException exception = PreRegistrationException.invalidEmployeeType();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("존재하지 않는 사원구분입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 명패내용 예외를 생성한다")
    void createInvalidNameplateContentException() {
        PreRegistrationException exception = PreRegistrationException.invalidNameplateContent();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("존재하지 않는 명패내용입니다.");
    }
}
