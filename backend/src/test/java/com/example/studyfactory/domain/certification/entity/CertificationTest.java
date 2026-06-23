package com.example.studyfactory.domain.certification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.certification.dto.CertificationCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("자격증 도메인 테스트")
class CertificationTest {

    @Test
    @DisplayName("자격증 생성 요청 DTO로 자격증 엔티티를 생성한다")
    void createCertificationFromRequest() {
        CertificationCreateRequest request = new CertificationCreateRequest(" 홍길동 매니저 ");

        Certification certification = request.toEntity();

        assertThat(certification.getContent()).isEqualTo("홍길동 매니저");
    }
}
