package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.service.SeatService;
import java.time.LocalDate;
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
@DisplayName("회원 조회 서비스 테스트")
class MemberFindServiceTest {

    private static final long OPERATOR_ID = 99L;

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SeatService seatService;

    @Test
    @DisplayName("전체 사원 목록을 ID 오름차순으로 조회한다")
    void findAllMembers() {
        givenAdminOperator();
        Member firstMember = createMember("kim", 10);
        Member secondMember = createMember("lee", 11);
        ReflectionTestUtils.setField(firstMember, "id", 1L);
        ReflectionTestUtils.setField(secondMember, "id", 2L);
        given(memberRepository.findAllByOrderByIdAsc())
                .willReturn(List.of(firstMember, secondMember));

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, null, null);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(MemberResponse::id)
                .containsExactly(1L, 2L);
        assertThat(responses).extracting(MemberResponse::name)
                .containsExactly("kim", "lee");
    }

    @Test
    @DisplayName("사원이 없으면 빈 목록을 반환한다")
    void findAllMembersWhenEmpty() {
        givenAdminOperator();
        given(memberRepository.findAllByOrderByIdAsc())
                .willReturn(List.of());

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, null, null);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("이름이 입력되면 해당 이름이 포함된 사원 목록을 조회한다")
    void findAllMembersByName() {
        givenAdminOperator();
        Member member = createMember("kim", 10);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByNameContainingOrderByIdAsc("ki"))
                .willReturn(List.of(member));

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, " ki ", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
    }

    @Test
    @DisplayName("이름이 공백이면 전체 사원 목록을 조회한다")
    void findAllMembersWhenNameIsBlank() {
        givenAdminOperator();
        Member member = createMember("kim", 10);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findAllByOrderByIdAsc())
                .willReturn(List.of(member));

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, " ", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
    }

    @Test
    @DisplayName("지점 ID가 입력되면 해당 지점의 사원 목록을 조회한다")
    void findAllMembersByBranchId() {
        givenAdminOperator();
        Member member = createMember("kim", 10, 1L);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(1L))
                .willReturn(List.of(member));

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, null, 1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).branchId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이름과 지점 ID가 모두 입력되면 두 조건에 맞는 사원 목록을 조회한다")
    void findAllMembersByNameAndBranchId() {
        givenAdminOperator();
        Member member = createMember("kim", 10, 1L);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByNameContainingAndReferenceInformationBranchIdOrderByIdAsc("ki", 1L))
                .willReturn(List.of(member));

        List<MemberResponse> responses = memberService.findAll(OPERATOR_ID, " ki ", 1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
        assertThat(responses.get(0).branchId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("스태프가 지점을 생략하면 본인 지점 사원만 조회한다")
    void staffFindsOnlyOwnBranchWhenBranchIsMissing() {
        Member staff = createMember("staff", 1, 2L, MemberRole.STAFF);
        ReflectionTestUtils.setField(staff, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(2L)).willReturn(List.of());

        memberService.findAll(OPERATOR_ID, null, null);

        org.mockito.BDDMockito.then(memberRepository).should()
                .findByReferenceInformationBranchIdOrderByIdAsc(2L);
    }

    @Test
    @DisplayName("스태프가 다른 지점 사원을 조회하면 예외가 발생한다")
    void rejectStaffCrossBranchLookup() {
        Member staff = createMember("staff", 1, 2L, MemberRole.STAFF);
        ReflectionTestUtils.setField(staff, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(staff));

        assertThatThrownBy(() -> memberService.findAll(OPERATOR_ID, null, 3L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원은 사원 목록을 조회할 수 없다")
    void rejectMemberLookup() {
        Member member = createMember("member", 1, 2L, MemberRole.MEMBER);
        ReflectionTestUtils.setField(member, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(member));

        assertThatThrownBy(() -> memberService.findAll(OPERATOR_ID, null, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자는 모든 지점의 사전등록 대기 사원 목록을 조회한다")
    void adminFindsAllPendingPreRegistrations() {
        givenAdminOperator();
        Member member = new Member(1L, "kim", null, 10, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(member));

        List<MemberResponse> responses = memberService.findPendingPreRegistrations(OPERATOR_ID, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("kim");
    }

    @Test
    @DisplayName("관리자는 지점을 지정해 해당 지점의 사전등록 대기 회원만 조회한다")
    void adminFiltersPendingPreRegistrationsByBranch() {
        givenAdminOperator();
        Member pending = new Member(
                2L,
                "pending staff",
                null,
                MemberRole.STAFF,
                10,
                LocalDate.of(2026, 7, 1),
                3L,
                null
        );
        ReflectionTestUtils.setField(pending, "id", 1L);
        given(memberRepository.findByReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(2L))
                .willReturn(List.of(pending));

        List<MemberResponse> responses = memberService.findPendingPreRegistrations(OPERATOR_ID, 2L);

        assertThat(responses).extracting(MemberResponse::branchId).containsExactly(2L);
        assertThat(responses).extracting(MemberResponse::role).containsExactly(MemberRole.STAFF);
        org.mockito.BDDMockito.then(memberRepository).should(org.mockito.Mockito.never())
                .findPendingPreRegistrations(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("스태프는 자기 지점의 사전등록 대기 사원만 조회한다")
    void staffFindsOnlyOwnBranchPendingPreRegistrations() {
        Member staff = createMember("staff", 1, 2L, MemberRole.STAFF);
        ReflectionTestUtils.setField(staff, "id", OPERATOR_ID);
        Member pending = new Member(2L, "kim", null, 10, LocalDate.of(2026, 7, 1), 3L, null);
        ReflectionTestUtils.setField(pending, "id", 1L);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(staff));
        given(memberRepository.findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                2L,
                MemberRole.MEMBER
        ))
                .willReturn(List.of(pending));

        List<MemberResponse> responses = memberService.findPendingPreRegistrations(OPERATOR_ID, null);

        assertThat(responses).extracting(MemberResponse::branchId).containsExactly(2L);
        org.mockito.BDDMockito.then(memberRepository).should()
                .findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(2L, MemberRole.MEMBER);
        org.mockito.BDDMockito.then(memberRepository).should(org.mockito.Mockito.never())
                .findPendingPreRegistrations(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("스태프는 다른 지점의 사전등록 대기 목록을 지정할 수 없다")
    void rejectStaffFilteringPendingPreRegistrationsByAnotherBranch() {
        Member staff = createMember("staff", 1, 2L, MemberRole.STAFF);
        ReflectionTestUtils.setField(staff, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(staff));

        assertThatThrownBy(() -> memberService.findPendingPreRegistrations(OPERATOR_ID, 3L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        org.mockito.BDDMockito.then(memberRepository).should(org.mockito.Mockito.never())
                .findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    @DisplayName("일반 회원은 사전등록 대기 목록을 조회할 수 없다")
    void memberCannotFindPendingPreRegistrations() {
        Member member = createMember("member", 1, 1L, MemberRole.MEMBER);
        ReflectionTestUtils.setField(member, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(member));

        assertThatThrownBy(() -> memberService.findPendingPreRegistrations(OPERATOR_ID, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member createMember(String name, int seatNumber) {
        return createMember(name, seatNumber, 1L);
    }

    private Member createMember(String name, int seatNumber, Long branchId) {
        return createMember(name, seatNumber, branchId, MemberRole.MEMBER);
    }

    private Member createMember(String name, int seatNumber, Long branchId, MemberRole role) {
        return new Member(
                branchId,
                name,
                "password123",
                role,
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }

    private void givenAdminOperator() {
        Member admin = createMember("admin", 1, 1L, MemberRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", OPERATOR_ID);
        given(memberRepository.findById(OPERATOR_ID)).willReturn(java.util.Optional.of(admin));
    }
}
