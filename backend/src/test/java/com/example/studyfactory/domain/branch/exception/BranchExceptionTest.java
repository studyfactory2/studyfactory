package com.example.studyfactory.domain.branch.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("지점 예외 테스트")
class BranchExceptionTest {

    @Test
    @DisplayName("중복된 지점 이름 예외를 생성한다")
    void createDuplicatedNameException() {
        BranchException exception = BranchException.duplicatedName();

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).isEqualTo("이미 등록된 지점입니다.");
    }
}
