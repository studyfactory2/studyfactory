package com.example.studyfactory.domain.nameplate.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.nameplate.dto.NameplateContentCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("명패내용 도메인 테스트")
class NameplateContentTest {

    @Test
    @DisplayName("명패내용 생성 요청 DTO로 명패내용 엔티티를 생성한다")
    void createNameplateContentFromRequest() {
        NameplateContentCreateRequest request = new NameplateContentCreateRequest(" 홍길동 매니저 ");

        NameplateContent nameplateContent = request.toEntity();

        assertThat(nameplateContent.getContent()).isEqualTo("홍길동 매니저");
    }
}
