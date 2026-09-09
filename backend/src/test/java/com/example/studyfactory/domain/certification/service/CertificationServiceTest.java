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
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
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

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("자격증을 저장하고 응답을 반환한다")
    void createCertification() {
        CertificationCreateRequest request = new CertificationCreateRequest(" 홍길동 매니저 ");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.ADMIN)));
        given(certificationRepository.existsByContent("홍길동 매니저")).willReturn(false);
        given(certificationRepository.save(any(Certification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CertificationResponse response = certificationService.create(1L, request);

        assertThat(response.content()).isEqualTo("홍길동 매니저");
        verify(certificationRepository).save(any(Certification.class));
    }

    @Test
    @DisplayName("이미 등록된 자격증이면 예외가 발생한다")
    void throwExceptionWhenCertificationIsDuplicated() {
        CertificationCreateRequest request = new CertificationCreateRequest("홍길동 매니저");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.ADMIN)));
        given(certificationRepository.existsByContent("홍길동 매니저")).willReturn(true);

        assertThatThrownBy(() -> certificationService.create(1L, request))
                .isInstanceOf(CertificationException.class)
                .hasMessageContaining("이미 등록된 자격증입니다.");
    }

    @Test
    @DisplayName("스태프는 자격증을 생성할 수 없다")
    void rejectCertificationCreationByStaff() {
        CertificationCreateRequest request = new CertificationCreateRequest("회계사");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(MemberRole.STAFF)));

        assertThatThrownBy(() -> certificationService.create(1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member member(MemberRole role) {
        return new Member(
                1L,
                "운영자",
                "password",
                role,
                null,
                LocalDate.of(2026, 9, 1),
                null
        );
    }
}
