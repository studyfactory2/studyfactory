package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
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
@DisplayName("음료 서비스 테스트")
class BeverageServiceTest {

    @InjectMocks
    private BeverageService beverageService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeveragePreferenceRepository beveragePreferenceRepository;

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정과 참고사항을 수정한다")
    void updateDrink() {
        Member member = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.updateDrink(1L, new BeverageRequest("따뜻한 라떼", "시럽 추가"));

        assertThat(response.drinks()).isEqualTo("따뜻한 라떼");
        assertThat(response.notes()).isEqualTo("시럽 추가");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 기존 음료 설정에 새 음료를 추가한다")
    void addDrink() {
        Member member = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라", "제로칼로리로 해주세요");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.addDrink(1L, new BeverageRequest("사이다\n식혜", "차갑게 주세요"));

        assertThat(response.drinks()).isEqualTo("콜라\n사이다\n식혜");
        assertThat(response.notes()).isEqualTo("차갑게 주세요");
    }

    @Test
    @DisplayName("스태프가 다른 사원의 음료 설정에 새 음료를 추가한다")
    void addDrinkForMemberByStaff() {
        Member staff = createMember(2L, MemberRole.STAFF);
        Member targetMember = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라", "제로칼로리로 해주세요");
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetMember));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.addDrinkForMember(2L, 1L, new BeverageRequest("사이다", "차갑게 주세요"));

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.drinks()).isEqualTo("콜라\n사이다");
        assertThat(response.notes()).isEqualTo("차갑게 주세요");
    }

    @Test
    @DisplayName("일반 사원이 다른 사원의 음료 설정에 새 음료를 추가하면 예외가 발생한다")
    void throwExceptionWhenAddDrinkForMemberWithoutPermission() {
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> beverageService.addDrinkForMember(1L, 2L, new BeverageRequest("사이다", "차갑게 주세요")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("음료 설정이 없으면 새 음료 설정을 생성한다")
    void addDrinkWhenBeveragePreferenceDoesNotExist() {
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.empty());
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.addDrink(1L, new BeverageRequest("콜라", "제로칼로리로 해주세요"));

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.drinks()).isEqualTo("콜라");
        assertThat(response.notes()).isEqualTo("제로칼로리로 해주세요");
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 수정하면 예외가 발생한다")
    void throwExceptionWhenUpdateDrinkMemberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> beverageService.updateDrink(1L, new BeverageRequest("따뜻한 라떼", "시럽 추가")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 추가하면 예외가 발생한다")
    void throwExceptionWhenAddDrinkMemberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> beverageService.addDrink(1L, new BeverageRequest("콜라", "제로칼로리로 해주세요")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정의 특정 항목을 삭제한다")
    void deleteDrinkItem() {
        Member member = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라\n사이다\n식혜", "차갑게 주세요");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.deleteDrinkItem(1L, "식혜");

        assertThat(response.drinks()).isEqualTo("콜라\n사이다");
        assertThat(response.notes()).isEqualTo("차갑게 주세요");
    }

    @Test
    @DisplayName("존재하지 않는 음료 항목을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteDrinkItemNotFound() {
        Member member = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라\n사이다", "차갑게 주세요");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));

        assertThatThrownBy(() -> beverageService.deleteDrinkItem(1L, "식혜"))
                .isInstanceOf(BeverageException.class)
                .hasMessageContaining("존재하지 않는 음료입니다.");
    }

    @Test
    @DisplayName("스태프가 다른 사원의 음료 설정에서 특정 항목을 삭제한다")
    void deleteDrinkItemForMemberByStaff() {
        Member staff = createMember(2L, MemberRole.STAFF);
        Member targetMember = createMember(1L, MemberRole.MEMBER);
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "콜라\n사이다\n식혜", "차갑게 주세요");
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetMember));
        given(beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).willReturn(Optional.of(beveragePreference));
        given(beveragePreferenceRepository.save(any(BeveragePreference.class))).willAnswer(invocation -> invocation.getArgument(0));

        BeveragePreferenceResponse response = beverageService.deleteDrinkItemForMember(2L, 1L, "식혜");

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.drinks()).isEqualTo("콜라\n사이다");
    }

    @Test
    @DisplayName("일반 사원이 다른 사원의 음료 항목을 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteDrinkItemForMemberWithoutPermission() {
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> beverageService.deleteDrinkItemForMember(1L, 2L, "식혜"))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("토큰의 사원 ID로 음료 설정과 참고사항을 삭제한다")
    void deleteDrink() {
        BeveragePreference beveragePreference = new BeveragePreference(1L, 1L, "아이스 아메리카노", "연하게");
        given(memberRepository.existsById(1L)).willReturn(true);
        given(beveragePreferenceRepository.findByMemberId(1L)).willReturn(List.of(beveragePreference));

        beverageService.deleteDrink(1L);

        then(beveragePreferenceRepository).should().deleteAll(List.of(beveragePreference));
    }

    @Test
    @DisplayName("존재하지 않는 사원의 음료 정보를 삭제하면 예외가 발생한다")
    void throwExceptionWhenDeleteDrinkMemberNotFound() {
        given(memberRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> beverageService.deleteDrink(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
    }

    private Member createMember(Long id, MemberRole role) {
        Member member = new Member(1L, "hong", "password123", role, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
