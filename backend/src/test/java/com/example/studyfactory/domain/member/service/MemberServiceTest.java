package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.MemberUpdateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
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
@DisplayName("회원 서비스 테스트")
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeverageService beverageService;

    @Mock
    private MemberDeletionCleanupService memberDeletionCleanupService;

    @Test
    @DisplayName("이름과 지점에 해당하는 사전등록 사원 정보를 확인한다")
    void verifyPreRegistration() {
        Member member = createPreRegisteredMember();
        BeverageItem beverageItem = new BeverageItem(1L, "아이스 아메리카노", "연하게");
        given(memberRepository.findByNameAndReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc("hong", 1L))
                .willReturn(List.of(member));
        given(beverageService.findItems(member.getId())).willReturn(List.of(beverageItem));

        List<PreRegistrationVerifyResponse> responses = memberService.verifyPreRegistration(
                new PreRegistrationVerifyRequest(" hong ", 1L)
        );
        PreRegistrationVerifyResponse response = responses.get(0);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.certificationId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNotes()).containsEntry("아이스 아메리카노", "연하게");
    }

    @Test
    @DisplayName("사전등록된 사원에 비밀번호를 세팅해 회원가입을 완료한다")
    void signup() {
        Member member = createPreRegisteredMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).willReturn(false);

        MemberSignupResponse response = memberService.signup(new MemberSignupRequest(1L, "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.joinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.certificationId()).isEqualTo(3L);
        assertThat(member.getPassword()).isEqualTo("password123");
    }

    @Test
    @DisplayName("일치하는 사전등록 사원 정보가 없으면 예외가 발생한다")
    void throwExceptionWhenPreRegistrationDoesNotExist() {
        given(memberRepository.findByNameAndReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc("hong", 1L))
                .willReturn(List.of());

        assertThatThrownBy(() -> memberService.verifyPreRegistration(new PreRegistrationVerifyRequest("hong", 1L)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");
    }

    @Test
    @DisplayName("이미 비밀번호가 있는 사원을 가입하면 예외가 발생한다")
    void throwExceptionWhenAlreadySignedUp() {
        Member member = createRegisteredMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest(1L, "password123")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("이미 가입된 사원입니다.");
    }

    @Test
    @DisplayName("관리자가 사원 정보를 수정한다")
    void updateMemberByAdmin() {
        Member admin = createRegisteredMember(MemberRole.ADMIN);
        Member member = createRegisteredMember();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberUpdateRequest request = new MemberUpdateRequest(
                3L,
                " kim ",
                MemberRole.STAFF,
                20,
                LocalDate.of(2026, 8, 1),
                4L,
                "회계사\n세무사"
        );

        assertThat(memberService.update(2L, 1L, request).name()).isEqualTo("kim");
        assertThat(member.getBranchId()).isEqualTo(3L);
        assertThat(member.getRole()).isEqualTo(MemberRole.STAFF);
        assertThat(member.getSeatNumber()).isEqualTo(20);
        assertThat(member.getJoinDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(member.getCertificationId()).isEqualTo(4L);
        assertThat(member.getPreparingCertifications()).isEqualTo("회계사\n세무사");
    }

    @Test
    @DisplayName("일반 사원이 사원 정보를 수정하면 예외가 발생한다")
    void throwExceptionWhenUpdateMemberWithoutPermission() {
        Member member = createRegisteredMember();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberUpdateRequest request = new MemberUpdateRequest(
                1L,
                "kim",
                MemberRole.MEMBER,
                20,
                LocalDate.of(2026, 8, 1),
                null,
                ""
        );

        assertThatThrownBy(() -> memberService.update(1L, 2L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("스태프가 사원 정보와 관련 데이터를 삭제한다")
    void deleteMemberByStaff() {
        Member staff = createRegisteredMember(MemberRole.STAFF);
        Member member = createRegisteredMember();
        ReflectionTestUtils.setField(staff, "id", 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        memberService.delete(2L, 1L);

        then(memberDeletionCleanupService).should().cleanup(1L);
        then(memberRepository).should().delete(member);
    }

    private Member createPreRegisteredMember() {
        Member member = new Member(1L, "hong", null, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member createRegisteredMember() {
        return createRegisteredMember(MemberRole.MEMBER);
    }

    private Member createRegisteredMember(MemberRole role) {
        Member member = new Member(1L, "hong", "password123", 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        if (role != MemberRole.MEMBER) {
            member = new Member(1L, "hong", "password123", role, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        }
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
