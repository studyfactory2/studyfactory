package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.member.dto.DrinkRequest;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.entity.ReferenceInformation;
import com.example.studyfactory.domain.preRegistration.entity.SubInformation;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 서비스 테스트")
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private PreRegistrationRepository preRegistrationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("이름과 지점에 해당하는 사전등록 정보를 확인한다")
    void verifyPreRegistration() {
        PreRegistration preRegistration = createPreRegistration();
        given(preRegistrationRepository.findByNameAndBranchId("hong", 1L))
                .willReturn(Optional.of(preRegistration));

        PreRegistrationVerifyResponse response = memberService.verifyPreRegistration(
                new PreRegistrationVerifyRequest(" hong ", 1L)
        );

        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.employeeTypeId()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.nameplateContentId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("사전등록 정보를 기반으로 회원가입을 완료한다")
    void signup() {
        PreRegistration preRegistration = createPreRegistration();
        given(preRegistrationRepository.getOrThrow(10L)).willReturn(preRegistration);
        given(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        MemberSignupResponse response = memberService.signup(new MemberSignupRequest(10L, "password123"));

        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.employeeTypeId()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.joinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.nameplateContentId()).isEqualTo(3L);
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("일치하는 사전등록 정보가 없으면 예외가 발생한다")
    void throwExceptionWhenPreRegistrationDoesNotExist() {
        given(preRegistrationRepository.findByNameAndBranchId("hong", 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.verifyPreRegistration(
                new PreRegistrationVerifyRequest("hong", 1L)
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");
    }

    @Test
    @DisplayName("이름과 지점과 비밀번호가 모두 같은 가입 정보가 있으면 예외가 발생한다")
    void throwExceptionWhenSameNameBranchAndPasswordAlreadyExist() {
        PreRegistration preRegistration = createPreRegistration();
        given(preRegistrationRepository.getOrThrow(10L)).willReturn(preRegistration);
        given(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest(10L, "password123")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("이미 가입된 사원입니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정과 참고사항을 수정한다")
    void updateDrink() {
        Member member = createMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberResponse response = memberService.updateDrink(1L, new DrinkRequest("따뜻한 라떼", "시럽 추가"));

        assertThat(response.drinkSetting()).isEqualTo("따뜻한 라떼");
        assertThat(response.drinkNote()).isEqualTo("시럽 추가");
        assertThat(response.memberNote()).isEqualTo("오전 교육 예정");
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 수정하면 예외가 발생한다")
    void throwExceptionWhenUpdateDrinkMemberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateDrink(1L, new DrinkRequest("따뜻한 라떼", "시럽 추가")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    private PreRegistration createPreRegistration() {
        return new PreRegistration(
                new ReferenceInformation(1L, 2L, 3L),
                "hong",
                12,
                LocalDate.of(2026, 7, 1),
                new SubInformation("아이스 아메리카노", "연하게", "오전 교육 예정")
        );
    }

    private Member createMember() {
        return new Member(
                1L,
                2L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
