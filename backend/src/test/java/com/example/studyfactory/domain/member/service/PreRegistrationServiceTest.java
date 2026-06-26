package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("사전등록 서비스 테스트")
class PreRegistrationServiceTest {

    @InjectMocks
    private PreRegistrationService preRegistrationService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeverageService beverageService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CertificationRepository certificationRepository;

    @Test
    @DisplayName("사전등록 요청으로 사원과 음료 정보를 저장하고 응답을 반환한다")
    void createPreRegistration() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(true);
        Certification certification = new Certification("홍길동 매니저");
        ReflectionTestUtils.setField(certification, "id", 3L);
        given(certificationRepository.findByContent("홍길동 매니저")).willReturn(Optional.of(certification));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, 1L, "아이스 아메리카노", "연하게"))
                .willReturn(new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게"));

        PreRegistrationResponse response = preRegistrationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.role()).isEqualTo(MemberRole.STAFF);
        assertThat(response.seatNumber()).isEqualTo(12);
        assertThat(response.expectedJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.certificationId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNote()).isEqualTo("연하게");
        assertThat(response.memberNote()).isEqualTo("오전 교육 예정");
        then(memberRepository).should().save(any(Member.class));
        then(beverageService).should().createPreference(1L, 1L, "아이스 아메리카노", "연하게");
    }

    @Test
    @DisplayName("직접 입력한 자격증이 기존에 없으면 새로 저장한 뒤 사원에 연결한다")
    void createPreRegistrationWithCustomCertification() {
        PreRegistrationCreateRequest request = new PreRegistrationCreateRequest(
                1L,
                " hong ",
                MemberRole.STAFF,
                12,
                LocalDate.of(2026, 7, 1),
                " 회계사 ",
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
        given(branchRepository.existsById(1L)).willReturn(true);
        given(certificationRepository.findByContent("회계사")).willReturn(Optional.empty());
        given(certificationRepository.save(any(Certification.class))).willAnswer(invocation -> {
            Certification certification = invocation.getArgument(0);
            ReflectionTestUtils.setField(certification, "id", 7L);
            return certification;
        });
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, 1L, "아이스 아메리카노", "연하게"))
                .willReturn(new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게"));

        PreRegistrationResponse response = preRegistrationService.create(request);

        assertThat(response.certificationId()).isEqualTo(7L);
        then(certificationRepository).should(never()).existsById(any());
        then(certificationRepository).should().save(any(Certification.class));
    }

    @Test
    @DisplayName("자격증이 비어있으면 null로 사원을 저장한다")
    void createPreRegistrationWithoutCertification() {
        PreRegistrationCreateRequest request = new PreRegistrationCreateRequest(
                1L,
                " hong ",
                MemberRole.STAFF,
                12,
                LocalDate.of(2026, 7, 1),
                " ",
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
        given(branchRepository.existsById(1L)).willReturn(true);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, 1L, "아이스 아메리카노", "연하게"))
                .willReturn(new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게"));

        PreRegistrationResponse response = preRegistrationService.create(request);

        assertThat(response.certificationId()).isNull();
        then(certificationRepository).should(never()).findByContent(any());
    }

    @Test
    @DisplayName("존재하지 않는 지점이면 예외가 발생한다")
    void throwExceptionWhenBranchDoesNotExist() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> preRegistrationService.create(request))
                .isInstanceOf(PreRegistrationException.class)
                .hasMessageContaining("존재하지 않는 지점입니다.");
    }

    private PreRegistrationCreateRequest createRequest() {
        return new PreRegistrationCreateRequest(
                1L,
                " hong ",
                MemberRole.STAFF,
                12,
                LocalDate.of(2026, 7, 1),
                "홍길동 매니저",
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
