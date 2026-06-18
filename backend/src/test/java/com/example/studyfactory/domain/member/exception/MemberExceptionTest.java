package com.example.studyfactory.domain.member.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("회원 예외 테스트")
class MemberExceptionTest {

    @Test
    @DisplayName("사전등록 정보 없음 예외를 생성한다")
    void createPreRegistrationNotFoundException() {
        MemberException exception = MemberException.preRegistrationNotFound();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("일치하는 사전등록 정보가 없습니다.");
    }

    @Test
    @DisplayName("이미 가입된 사원 예외를 생성한다")
    void createAlreadySignedUpException() {
        MemberException exception = MemberException.alreadySignedUp();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 가입된 사원입니다.");
    }
}
