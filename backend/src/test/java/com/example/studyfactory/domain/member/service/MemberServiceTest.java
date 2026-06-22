package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.dto.DrinkRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 서비스 테스트")
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeveragePreferenceRepository beveragePreferenceRepository;

    @Test
    @DisplayName("이름과 지점에 해당하는 사전등록 사원 정보를 확인한다")
    void verifyPreRegistration() {
        Member member = createPreRegisteredMember();
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게");
        given(memberRepository.findByNameAndBranchId("hong", 1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));

        PreRegistrationVerifyResponse response = memberService.verifyPreRegistration(
                new PreRegistrationVerifyRequest(" hong ", 1L)
        );

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.nameplateContentId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNote()).isEqualTo("연하게");
    }

    @Test
    @DisplayName("사전등록된 사원에 비밀번호를 세팅해 회원가입을 완료한다")
    void signup() {
        Member member = createPreRegisteredMember();
        given(memberRepository.findByNameAndBranchId("hong", 1L)).willReturn(Optional.of(member));

        MemberSignupResponse response = memberService.signup(new MemberSignupRequest(" hong ", 1L, "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.joinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.nameplateContentId()).isEqualTo(3L);
        assertThat(member.getPassword()).isEqualTo("password123");
    }

    @Test
    @DisplayName("일치하는 사전등록 사원 정보가 없으면 예외가 발생한다")
    void throwExceptionWhenPreRegistrationDoesNotExist() {
        given(memberRepository.findByNameAndBranchId("hong", 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.verifyPreRegistration(new PreRegistrationVerifyRequest("hong", 1L)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");
    }

    @Test
    @DisplayName("이미 비밀번호가 있는 사원을 가입하면 예외가 발생한다")
    void throwExceptionWhenAlreadySignedUp() {
        Member member = createRegisteredMember();
        given(memberRepository.findByNameAndBranchId("hong", 1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest("hong", 1L, "password123")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("이미 가입된 사원입니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정과 참고사항을 수정한다")
    void updateDrink() {
        Member member = createRegisteredMember();
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = memberService.updateDrink(1L, new DrinkRequest("따뜻한 라떼", "시럽 추가"));

        assertThat(response.drinks()).isEqualTo("따뜻한 라떼");
        assertThat(response.notes()).isEqualTo("시럽 추가");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 기존 음료 설정에 새 음료를 추가한다")
    void addDrink() {
        Member member = createRegisteredMember();
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라", "제로칼로리로 해주세요");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = memberService.addDrink(1L, new DrinkRequest("사이다\n식혜", "차갑게 주세요"));

        assertThat(response.drinks()).isEqualTo("콜라\n사이다\n식혜");
        assertThat(response.notes()).isEqualTo("차갑게 주세요");
    }

    @Test
    @DisplayName("음료 설정이 없으면 새 음료 설정을 생성한다")
    void addDrinkWhenBeveragePreferenceDoesNotExist() {
        Member member = createRegisteredMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.empty());
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = memberService.addDrink(1L, new DrinkRequest("콜라", "제로칼로리로 해주세요"));

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.drinks()).isEqualTo("콜라");
        assertThat(response.notes()).isEqualTo("제로칼로리로 해주세요");
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 수정하면 예외가 발생한다")
    void throwExceptionWhenUpdateDrinkMemberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateDrink(1L, new DrinkRequest("따뜻한 라떼", "시럽 추가")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 추가하면 예외가 발생한다")
    void throwExceptionWhenAddDrinkMemberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.addDrink(1L, new DrinkRequest("콜라", "제로칼로리로 해주세요")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정과 참고사항을 삭제한다")
    void deleteDrink() {
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게");
        given(memberRepository.existsById(1L)).willReturn(true);
        given(beveragePreferenceRepository.findByMemberId(1L)).willReturn(List.of(beveragePreference));

        memberService.deleteDrink(1L);

        then(beveragePreferenceRepository).should().deleteAll(List.of(beveragePreference));
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteDrinkMemberNotFound() {
        given(memberRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> memberService.deleteDrink(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    private Member createPreRegisteredMember() {
        Member member = new Member(1L, "hong", null, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member createRegisteredMember() {
        Member member = new Member(1L, "hong", "password123", 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
