package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import com.example.studyfactory.domain.room.exception.SeatException;
import com.example.studyfactory.domain.room.service.SeatService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("사전등록 서비스 테스트")
class PreRegistrationServiceTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long STAFF_ID = 8L;
    private static final Long MEMBER_ID = 7L;

    @InjectMocks
    private PreRegistrationService preRegistrationService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeverageService beverageService;

    @Mock
    private MemberDeletionCleanupService memberDeletionCleanupService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CertificationRepository certificationRepository;

    @Mock
    private SeatService seatService;

    @Test
    @DisplayName("사전등록 요청으로 사원과 음료 정보를 저장하고 응답을 반환한다")
    void createPreRegistration() {
        PreRegistrationCreateRequest request = createRequest();
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        given(branchRepository.existsById(1L)).willReturn(true);
        Certification certification = new Certification("홍길동 매니저");
        ReflectionTestUtils.setField(certification, "id", 3L);
        given(certificationRepository.findByContent("홍길동 매니저")).willReturn(Optional.of(certification));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, "아이스 아메리카노", "연하게"))
                .willReturn(List.of(new BeverageItem(1L, "아이스 아메리카노", "연하게")));

        PreRegistrationResponse response = preRegistrationService.create(ADMIN_ID, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.role()).isEqualTo(MemberRole.STAFF);
        assertThat(response.seatNumber()).isEqualTo(12);
        assertThat(response.expectedJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.certificationId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNotes()).containsEntry("아이스 아메리카노", "연하게");
        then(memberRepository).should().save(any(Member.class));
        then(beverageService).should().createPreference(1L, "아이스 아메리카노", "연하게");
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
                "연하게"
        );
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
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
        given(beverageService.createPreference(1L, "아이스 아메리카노", "연하게"))
                .willReturn(List.of(new BeverageItem(1L, "아이스 아메리카노", "연하게")));

        PreRegistrationResponse response = preRegistrationService.create(ADMIN_ID, request);

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
                "연하게"
        );
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        given(branchRepository.existsById(1L)).willReturn(true);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, "아이스 아메리카노", "연하게"))
                .willReturn(List.of(new BeverageItem(1L, "아이스 아메리카노", "연하게")));

        PreRegistrationResponse response = preRegistrationService.create(ADMIN_ID, request);

        assertThat(response.certificationId()).isNull();
        then(certificationRepository).should(never()).findByContent(any());
    }

    @Test
    @DisplayName("존재하지 않는 지점이면 예외가 발생한다")
    void throwExceptionWhenBranchDoesNotExist() {
        PreRegistrationCreateRequest request = createRequest();
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        given(branchRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> preRegistrationService.create(ADMIN_ID, request))
                .isInstanceOf(PreRegistrationException.class)
                .hasMessageContaining("존재하지 않는 지점입니다.");
    }

    @Test
    @DisplayName("이미 배정된 좌석으로 사전등록하면 예외가 발생한다")
    void throwExceptionWhenSeatAlreadyAssigned() {
        PreRegistrationCreateRequest request = createRequest();
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        given(branchRepository.existsById(1L)).willReturn(true);
        willThrow(SeatException.alreadyAssigned()).given(seatService).validateAssignment(null, 1L, 12);

        assertThatThrownBy(() -> preRegistrationService.create(ADMIN_ID, request))
                .isInstanceOf(SeatException.class)
                .hasMessageContaining("이미 배정된 좌석입니다.");
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("좌석표에 없거나 문 위치인 번호로 사전등록할 수 없다")
    void rejectNonSeatLayoutItem() {
        PreRegistrationCreateRequest request = createRequest();
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        given(branchRepository.existsById(1L)).willReturn(true);
        willThrow(SeatException.invalidSeat()).given(seatService).validateAssignment(null, 1L, 12);

        assertThatThrownBy(() -> preRegistrationService.create(ADMIN_ID, request))
                .isInstanceOf(SeatException.class)
                .hasMessageContaining("존재하지 않는 좌석입니다.");
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("일반 회원은 사전등록을 생성할 수 없다")
    void rejectPreRegistrationForMemberOperator() {
        givenOperator(MEMBER_ID, MemberRole.MEMBER, 1L);

        assertThatThrownBy(() -> preRegistrationService.create(MEMBER_ID, createRequest()))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("스태프는 관리자나 스태프 권한으로 사전등록할 수 없다")
    void rejectPrivilegedRoleForStaffOperator() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 1L);

        assertThatThrownBy(() -> preRegistrationService.create(STAFF_ID, createRequest()))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("스태프는 다른 지점에 사전등록할 수 없다")
    void rejectOtherBranchForStaffOperator() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 2L);

        assertThatThrownBy(() -> preRegistrationService.create(STAFF_ID, createMemberRequest()))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("스태프는 자기 지점에 일반 회원을 사전등록할 수 있다")
    void allowOwnBranchMemberForStaffOperator() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 1L);
        given(branchRepository.existsById(1L)).willReturn(true);
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });
        given(beverageService.createPreference(1L, "아이스 아메리카노", "연하게"))
                .willReturn(List.of(new BeverageItem(1L, "아이스 아메리카노", "연하게")));

        PreRegistrationResponse response = preRegistrationService.create(STAFF_ID, createMemberRequest());

        assertThat(response.role()).isEqualTo(MemberRole.MEMBER);
        then(memberRepository).should().save(any(Member.class));
    }

    @Test
    @DisplayName("관리자는 모든 지점의 사전등록 대기 목록을 조회한다")
    void adminFindsAllPendingPreRegistrations() {
        givenOperator(ADMIN_ID, MemberRole.ADMIN, 1L);
        Member first = createPendingMember(1L, 1L, "강남 회원");
        Member second = createPendingMember(2L, 2L, "홍대 회원");
        given(memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(first, second));
        given(beverageService.findItems(first.getId())).willReturn(List.of());
        given(beverageService.findItems(second.getId())).willReturn(List.of());

        List<PreRegistrationResponse> responses = preRegistrationService.findPending(ADMIN_ID);

        assertThat(responses).extracting(PreRegistrationResponse::branchId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("스태프는 자기 지점의 사전등록 대기 목록만 조회한다")
    void staffFindsOnlyOwnBranchPendingPreRegistrations() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 2L);
        Member pending = createPendingMember(1L, 2L, "홍대 회원");
        given(memberRepository.findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                2L,
                MemberRole.MEMBER
        ))
                .willReturn(List.of(pending));
        given(beverageService.findItems(pending.getId())).willReturn(List.of());

        List<PreRegistrationResponse> responses = preRegistrationService.findPending(STAFF_ID);

        assertThat(responses).extracting(PreRegistrationResponse::branchId).containsExactly(2L);
        then(memberRepository).should(never()).findPendingPreRegistrations(any());
    }

    @Test
    @DisplayName("스태프는 같은 지점의 관리자 사전등록 정보를 수정할 수 없다")
    void staffCannotUpdatePrivilegedPendingTarget() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 1L);
        Member pendingStaff = createPendingMember(1L, 1L, "예정 스태프", MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(pendingStaff));

        assertThatThrownBy(() -> preRegistrationService.update(STAFF_ID, 1L, createMemberRequest()))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(beverageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("스태프는 같은 지점의 관리자 사전등록 정보를 삭제할 수 없다")
    void staffCannotDeletePrivilegedPendingTarget() {
        givenOperator(STAFF_ID, MemberRole.STAFF, 1L);
        Member pendingAdmin = createPendingMember(1L, 1L, "예정 관리자", MemberRole.ADMIN);
        given(memberRepository.findById(1L)).willReturn(Optional.of(pendingAdmin));

        assertThatThrownBy(() -> preRegistrationService.delete(STAFF_ID, 1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(memberDeletionCleanupService).shouldHaveNoInteractions();
        then(memberRepository).should(never()).delete(pendingAdmin);
    }

    @Test
    @DisplayName("일반 회원은 사전등록 대기 목록을 조회할 수 없다")
    void memberCannotFindPendingPreRegistrations() {
        givenOperator(MEMBER_ID, MemberRole.MEMBER, 1L);

        assertThatThrownBy(() -> preRegistrationService.findPending(MEMBER_ID))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private void givenOperator(Long operatorId, MemberRole role, Long branchId) {
        Member operator = new Member(
                branchId,
                "운영자",
                "password123",
                role,
                1,
                LocalDate.of(2026, 7, 1),
                null
        );
        ReflectionTestUtils.setField(operator, "id", operatorId);
        given(memberRepository.findById(operatorId)).willReturn(Optional.of(operator));
    }

    private PreRegistrationCreateRequest createMemberRequest() {
        return new PreRegistrationCreateRequest(
                1L,
                " hong ",
                MemberRole.MEMBER,
                null,
                LocalDate.of(2026, 7, 1),
                null,
                "아이스 아메리카노",
                "연하게"
        );
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
                "연하게"
        );
    }

    private Member createPendingMember(Long id, Long branchId, String name) {
        return createPendingMember(id, branchId, name, MemberRole.MEMBER);
    }

    private Member createPendingMember(Long id, Long branchId, String name, MemberRole role) {
        Member member = new Member(
                branchId,
                name,
                null,
                role,
                null,
                LocalDate.of(2026, 7, 1),
                null,
                null
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
