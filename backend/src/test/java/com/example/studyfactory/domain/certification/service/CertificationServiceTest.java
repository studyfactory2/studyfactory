package com.example.studyfactory.domain.certification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.certification.dto.CertificationCreateRequest;
import com.example.studyfactory.domain.certification.dto.CertificationResponse;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.exception.CertificationException;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("자격증 서비스 테스트")
class CertificationServiceTest {

    @InjectMocks
    private CertificationService certificationService;

    @Mock
    private CertificationRepository certificationRepository;

    @Test
    @DisplayName("자격증을 저장하고 응답을 반환한다")
    void createCertification() {
        CertificationCreateRequest request = new CertificationCreateRequest(" 홍길동 매니저 ");
        given(certificationRepository.existsByContent("홍길동 매니저")).willReturn(false);
        given(certificationRepository.save(any(Certification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CertificationResponse response = certificationService.create(request);

        assertThat(response.content()).isEqualTo("홍길동 매니저");
        verify(certificationRepository).save(any(Certification.class));
    }

    @Test
    @DisplayName("이미 등록된 자격증이면 예외가 발생한다")
    void throwExceptionWhenCertificationIsDuplicated() {
        CertificationCreateRequest request = new CertificationCreateRequest("홍길동 매니저");
        given(certificationRepository.existsByContent("홍길동 매니저")).willReturn(true);

        assertThatThrownBy(() -> certificationService.create(request))
                .isInstanceOf(CertificationException.class)
                .hasMessageContaining("이미 등록된 자격증입니다.");
    }
}
