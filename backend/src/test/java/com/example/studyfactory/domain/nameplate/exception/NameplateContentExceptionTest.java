package com.example.studyfactory.domain.nameplate.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("명패내용 예외 테스트")
class NameplateContentExceptionTest {

    @Test
    @DisplayName("중복된 명패내용 예외를 생성한다")
    void createDuplicatedContentException() {
        NameplateContentException exception = NameplateContentException.duplicatedContent();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 등록된 명패내용입니다.");
    }
}
