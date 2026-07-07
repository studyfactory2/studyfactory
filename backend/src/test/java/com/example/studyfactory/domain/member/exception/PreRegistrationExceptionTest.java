package com.example.studyfactory.domain.member.exception;

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
    @DisplayName("존재하지 않는 자격증 예외를 생성한다")
    void createInvalidCertificationException() {
        PreRegistrationException exception = PreRegistrationException.invalidCertification();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("존재하지 않는 자격증입니다.");
    }

    @Test
    @DisplayName("이미 배정된 좌석 예외를 생성한다")
    void createSeatAlreadyAssignedException() {
        PreRegistrationException exception = PreRegistrationException.seatAlreadyAssigned();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 배정된 좌석입니다. 다른 좌석을 선택해주세요.");
    }
}
