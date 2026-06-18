package com.example.studyfactory.domain.adminDashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.studyfactory.domain.adminDashboard.dto.AdminDashboardMemberResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
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
@DisplayName("관리자 대시보드 사원 서비스 테스트")
class AdminDashboardMemberServiceTest {

    @InjectMocks
    private AdminDashboardMemberService adminDashboardMemberService;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("전체 사원 목록을 ID 오름차순으로 조회한다")
    void findAllMembers() {
        Member firstMember = createMember("kim", 10);
        Member secondMember = createMember("lee", 11);
        ReflectionTestUtils.setField(firstMember, "id", 1L);
        ReflectionTestUtils.setField(secondMember, "id", 2L);
        given(memberRepository.search(null, null, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(firstMember, secondMember));

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(null, null);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AdminDashboardMemberResponse::id)
                .containsExactly(1L, 2L);
        assertThat(responses).extracting(AdminDashboardMemberResponse::name)
                .containsExactly("kim", "lee");
    }

    @Test
    @DisplayName("사원이 없으면 빈 목록을 반환한다")
    void findAllMembersWhenEmpty() {
        given(memberRepository.search(null, null, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of());

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(null, null);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("이름이 입력되면 해당 이름이 포함된 사원 목록을 조회한다")
    void findAllMembersByName() {
        Member member = createMember("kim", 10);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.search("ki", null, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(member));

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(" ki ", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
    }

    @Test
    @DisplayName("이름이 공백이면 전체 사원 목록을 조회한다")
    void findAllMembersWhenNameIsBlank() {
        Member member = createMember("kim", 10);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.search(null, null, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(member));

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(" ", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
    }

    @Test
    @DisplayName("지점 ID가 입력되면 해당 지점의 사원 목록을 조회한다")
    void findAllMembersByBranchId() {
        Member member = createMember("kim", 10, 1L);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.search(null, 1L, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(member));

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(null, 1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).branchId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이름과 지점 ID가 모두 입력되면 두 조건에 맞는 사원 목록을 조회한다")
    void findAllMembersByNameAndBranchId() {
        Member member = createMember("kim", 10, 1L);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.search("ki", 1L, Sort.by(Sort.Direction.ASC, "id")))
                .willReturn(List.of(member));

        List<AdminDashboardMemberResponse> responses = adminDashboardMemberService.findAll(" ki ", 1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("kim");
        assertThat(responses.get(0).branchId()).isEqualTo(1L);
    }

    private Member createMember(String name, int seatNumber) {
        return createMember(name, seatNumber, 1L);
    }

    private Member createMember(String name, int seatNumber, Long branchId) {
        return new Member(
                branchId,
                2L,
                name,
                "password123",
                seatNumber,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
