package com.example.studyfactory.domain.certification.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("자격증 예외 테스트")
class CertificationExceptionTest {

    @Test
    @DisplayName("중복된 자격증 예외를 생성한다")
    void createDuplicatedContentException() {
        CertificationException exception = CertificationException.duplicatedContent();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 등록된 자격증입니다.");
    }
}
