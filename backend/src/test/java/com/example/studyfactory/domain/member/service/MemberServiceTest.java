package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.MemberUpdateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.exception.RegistrationCodeException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.exception.SeatException;
import com.example.studyfactory.domain.room.service.SeatService;
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

    @Mock
    private SeatService seatService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CertificationRepository certificationRepository;

    @Mock
    private RegistrationCodeService registrationCodeService;

    @Test
    @DisplayName("이름과 지점에 해당하는 사전등록 사원 정보를 확인한다")
    void verifyPreRegistration() {
        Member member = createPreRegisteredMember();
        BeverageItem beverageItem = new BeverageItem(1L, "아이스 아메리카노", "연하게");
        given(memberRepository.findByNameAndReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                "hong",
                1L,
                MemberRole.MEMBER
        ))
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
    @DisplayName("공개 사전등록 확인은 관리자나 스태프 계정을 노출하지 않는다")
    void verifyPreRegistrationDoesNotDiscoverPrivilegedAccounts() {
        given(memberRepository.findByNameAndReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                "manager",
                1L,
                MemberRole.MEMBER
        )).willReturn(List.of());

        assertThatThrownBy(() -> memberService.verifyPreRegistration(
                new PreRegistrationVerifyRequest("manager", 1L)
        ))
                .isInstanceOf(RegistrationCodeException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");

        then(beverageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사전등록된 사원에 비밀번호를 세팅해 회원가입을 완료한다")
    void signup() {
        Member member = createPreRegisteredMember();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(memberRepository.existsByNameAndBranchIdAndPassword("hong", 1L, "password123")).willReturn(false);

        MemberSignupResponse response = memberService.signup(new MemberSignupRequest(1L, "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.joinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.certificationId()).isEqualTo(3L);
        assertThat(member.getPassword()).isEqualTo("password123");
        then(memberRepository).should().findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("일치하는 사전등록 사원 정보가 없으면 예외가 발생한다")
    void throwExceptionWhenPreRegistrationDoesNotExist() {
        given(memberRepository.findByNameAndReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                "hong",
                1L,
                MemberRole.MEMBER
        ))
                .willReturn(List.of());

        assertThatThrownBy(() -> memberService.verifyPreRegistration(new PreRegistrationVerifyRequest("hong", 1L)))
                .isInstanceOf(RegistrationCodeException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");
    }

    @Test
    @DisplayName("이미 비밀번호가 있는 사원을 가입하면 예외가 발생한다")
    void throwExceptionWhenAlreadySignedUp() {
        Member member = createRegisteredMember();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest(1L, "password123")))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("이미 가입된 사원입니다.");
    }

    @Test
    @DisplayName("공개 회원가입은 잠긴 행을 다시 확인하고 관리자나 스태프 계정을 숨긴다")
    void rejectPrivilegedPublicSignupWithoutRevealingAccount() {
        Member pendingAdmin = createPreRegisteredMember(MemberRole.ADMIN);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(pendingAdmin));
        given(registrationCodeService.requiresCode(MemberRole.ADMIN)).willReturn(true);
        given(registrationCodeService.validate(pendingAdmin, null)).willReturn(false);

        assertThatThrownBy(() -> memberService.signup(new MemberSignupRequest(1L, "password123")))
                .isInstanceOf(RegistrationCodeException.class)
                .hasMessageContaining("일치하는 사전등록 정보가 없습니다.");

        assertThat(pendingAdmin.getPassword()).isNull();
        then(memberRepository).should().findByIdForUpdate(1L);
        then(memberRepository).should(never()).existsByNameAndBranchIdAndPassword(
                "hong",
                1L,
                "password123"
        );
    }

    @Test
    @DisplayName("관리자가 사원 정보를 수정한다")
    void updateMemberByAdmin() {
        Member admin = createRegisteredMember(MemberRole.ADMIN);
        Member member = createRegisteredMember();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(3L)).willReturn(true);
        given(certificationRepository.existsById(4L)).willReturn(true);

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
        then(seatService).should().validateAssignment(1L, 3L, 20);
    }

    @Test
    @DisplayName("회원 정보를 같은 지점의 기존 이름으로 수정할 수 없다")
    void rejectDuplicateNameWhenUpdatingMember() {
        Member admin = createRegisteredMember(MemberRole.ADMIN);
        Member member = createRegisteredMember();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(1L)).willReturn(true);
        given(memberRepository.existsByNameAndBranchIdExcludingMember("kim", 1L, 1L)).willReturn(true);

        MemberUpdateRequest request = new MemberUpdateRequest(
                1L,
                " kim ",
                MemberRole.MEMBER,
                12,
                LocalDate.of(2026, 8, 1),
                null,
                ""
        );

        assertThatThrownBy(() -> memberService.update(2L, 1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("같은 지점에 동일한 이름이 이미 있습니다.");

        assertThat(member.getName()).isEqualTo("hong");
    }

    @Test
    @DisplayName("스태프가 자기 지점 일반 회원 정보를 수정한다")
    void updateOwnBranchMemberByStaff() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.of(member));
        given(branchRepository.existsById(1L)).willReturn(true);
        MemberUpdateRequest request = updateRequest(1L, MemberRole.MEMBER, 20);

        assertThat(memberService.update(2L, 1L, request).seatNumber()).isEqualTo(20);

        then(seatService).should().validateAssignment(1L, 1L, 20);
    }

    @Test
    @DisplayName("스태프의 다른 지점 회원 수정은 존재하지 않는 회원과 같은 응답으로 거절한다")
    void rejectStaffUpdatingCrossBranchMember() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.update(2L, 1L, updateRequest(2L, MemberRole.MEMBER, null)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
        then(seatService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("스태프는 자기 지점 회원을 다른 지점으로 이동할 수 없다")
    void rejectStaffMovingMemberToAnotherBranch() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.update(2L, 1L, updateRequest(2L, MemberRole.MEMBER, null)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        assertThat(member.getBranchId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("스태프는 일반 회원을 관리자 역할로 승격할 수 없다")
    void rejectStaffPromotingMember() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.update(2L, 1L, updateRequest(1L, MemberRole.ADMIN, null)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    @DisplayName("회원 정보 수정에서도 실제 좌석표에 없는 좌석은 거절한다")
    void rejectInvalidSeatWhenUpdatingMember() {
        Member admin = createRegisteredMember(2L, MemberRole.ADMIN, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(1L)).willReturn(true);
        willThrow(SeatException.invalidSeat()).given(seatService).validateAssignment(1L, 1L, 999);

        assertThatThrownBy(() -> memberService.update(2L, 1L, updateRequest(1L, MemberRole.MEMBER, 999)))
                .isInstanceOf(SeatException.class)
                .hasMessageContaining("존재하지 않는 좌석입니다.");
        assertThat(member.getSeatNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("기존 좌석값을 바꾸지 않는 정보 수정은 과거 좌석표 누락값을 강제로 막지 않는다")
    void allowNonSeatFieldsUpdateForUnchangedLegacySeat() {
        Member admin = createRegisteredMember(2L, MemberRole.ADMIN, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        member.updateSeat(999);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(1L)).willReturn(true);

        assertThat(memberService.update(2L, 1L, updateRequest(1L, MemberRole.MEMBER, 999)).name())
                .isEqualTo("kim");

        then(seatService).shouldHaveNoInteractions();
        assertThat(member.getSeatNumber()).isEqualTo(999);
    }

    @Test
    @DisplayName("좌석 변경이 없어도 존재하지 않는 지점으로 회원 정보를 저장할 수 없다")
    void rejectInvalidBranchWhenUpdatingUnchangedSeat() {
        Member admin = createRegisteredMember(2L, MemberRole.ADMIN, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 999L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> memberService.update(
                2L,
                1L,
                updateRequest(999L, MemberRole.MEMBER, 12)
        ))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 지점입니다.");

        assertThat(member.getName()).isEqualTo("hong");
        then(seatService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 자격증으로 회원 정보를 저장할 수 없다")
    void rejectInvalidCertificationWhenUpdatingMember() {
        Member admin = createRegisteredMember(2L, MemberRole.ADMIN, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(branchRepository.existsById(1L)).willReturn(true);
        given(certificationRepository.existsById(999L)).willReturn(false);
        MemberUpdateRequest request = new MemberUpdateRequest(
                1L,
                "kim",
                MemberRole.MEMBER,
                12,
                LocalDate.of(2026, 8, 1),
                999L,
                ""
        );

        assertThatThrownBy(() -> memberService.update(2L, 1L, request))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 자격증입니다.");

        assertThat(member.getCertificationId()).isEqualTo(3L);
        then(seatService).shouldHaveNoInteractions();
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
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.of(member));

        memberService.delete(2L, 1L);

        then(memberDeletionCleanupService).should().cleanup(1L);
        then(memberRepository).should().findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L);
        then(memberRepository).should().delete(member);
    }

    @Test
    @DisplayName("스태프의 다른 지점 회원 삭제는 존재하지 않는 회원과 같은 응답으로 거절한다")
    void rejectStaffDeletingCrossBranchMember() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        Member member = createRegisteredMember(1L, MemberRole.MEMBER, 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.delete(2L, 1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
        then(memberDeletionCleanupService).shouldHaveNoInteractions();
        then(memberRepository).should(never()).delete(member);
    }

    @Test
    @DisplayName("스태프는 다른 스태프 계정을 삭제할 수 없다")
    void rejectStaffDeletingPrivilegedTarget() {
        Member staff = createRegisteredMember(2L, MemberRole.STAFF, 1L);
        Member targetStaff = createRegisteredMember(1L, MemberRole.STAFF, 1L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 1L))
                .willReturn(Optional.of(targetStaff));

        assertThatThrownBy(() -> memberService.delete(2L, 1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(memberRepository).should(never()).delete(targetStaff);
    }

    @Test
    @DisplayName("관리자는 다른 지점의 스태프 계정을 삭제할 수 있다")
    void deleteCrossBranchStaffByAdmin() {
        Member admin = createRegisteredMember(2L, MemberRole.ADMIN, 1L);
        Member targetStaff = createRegisteredMember(1L, MemberRole.STAFF, 2L);
        given(memberRepository.findById(2L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(targetStaff));

        memberService.delete(2L, 1L);

        then(memberDeletionCleanupService).should().cleanup(1L);
        then(memberRepository).should().delete(targetStaff);
    }

    private Member createPreRegisteredMember() {
        return createPreRegisteredMember(MemberRole.MEMBER);
    }

    private Member createPreRegisteredMember(MemberRole role) {
        Member member = new Member(
                1L,
                "hong",
                null,
                role,
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member createRegisteredMember() {
        return createRegisteredMember(MemberRole.MEMBER);
    }

    private Member createRegisteredMember(MemberRole role) {
        return createRegisteredMember(1L, role, 1L);
    }

    private Member createRegisteredMember(Long id, MemberRole role, Long branchId) {
        Member member = new Member(
                branchId,
                "hong",
                "password123",
                role,
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private MemberUpdateRequest updateRequest(Long branchId, MemberRole role, Integer seatNumber) {
        return new MemberUpdateRequest(
                branchId,
                "kim",
                role,
                seatNumber,
                LocalDate.of(2026, 8, 1),
                null,
                ""
        );
    }
}
