package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import java.time.LocalDate;
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
    private BeveragePreferenceRepository beveragePreferenceRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private NameplateContentRepository nameplateContentRepository;

    @Test
    @DisplayName("사전등록 요청으로 사원과 음료 정보를 저장하고 응답을 반환한다")
    void createPreRegistration() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(true);
        given(nameplateContentRepository.existsById(3L)).willReturn(true);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beveragePreferenceRepository.save(any(BeveragePreference.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PreRegistrationResponse response = preRegistrationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.role()).isEqualTo(MemberRole.STAFF);
        assertThat(response.seatNumber()).isEqualTo(12);
        assertThat(response.expectedJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.nameplateContentId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNote()).isEqualTo("연하게");
        assertThat(response.memberNote()).isEqualTo("오전 교육 예정");
        then(memberRepository).should().save(any(Member.class));
        then(beveragePreferenceRepository).should().save(any(BeveragePreference.class));
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

    @Test
    @DisplayName("존재하지 않는 명패내용이면 예외가 발생한다")
    void throwExceptionWhenNameplateContentDoesNotExist() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(true);
        given(nameplateContentRepository.existsById(3L)).willReturn(false);

        assertThatThrownBy(() -> preRegistrationService.create(request))
                .isInstanceOf(PreRegistrationException.class)
                .hasMessageContaining("존재하지 않는 명패내용입니다.");
    }

    private PreRegistrationCreateRequest createRequest() {
        return new PreRegistrationCreateRequest(
                1L,
                " hong ",
                MemberRole.STAFF,
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
