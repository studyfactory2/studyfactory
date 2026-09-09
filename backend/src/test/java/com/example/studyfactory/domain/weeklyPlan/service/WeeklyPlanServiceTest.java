package com.example.studyfactory.domain.weeklyPlan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanResponse;
import com.example.studyfactory.domain.weeklyPlan.repository.MonthlyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("주간 계획 서비스 테스트")
class WeeklyPlanServiceTest {

    /**
     * 2026-10-01 00:30 Asia/Seoul — still 2026-09-30 in UTC, so every assertion
     * below fails if the default falls back to the JVM zone.
     */
    private static final Instant JUST_AFTER_SEOUL_MONTH_ROLLOVER = Instant.parse("2026-09-30T15:30:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.of(2026, 10, 1);
    private static final String SEOUL_MONTH = "2026-10";

    @InjectMocks
    private WeeklyPlanService weeklyPlanService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WeeklyPlanGoalRepository weeklyPlanGoalRepository;

    @Mock
    private WeeklyPlanItemRepository weeklyPlanItemRepository;

    @Mock
    private MonthlyPlanGoalRepository monthlyPlanGoalRepository;

    @Spy
    private Clock clock = Clock.fixed(JUST_AFTER_SEOUL_MONTH_ROLLOVER, ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("주차를 지정하지 않으면 서울 기준 오늘로 조회한다")
    void resolveDefaultWeekInSeoul() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(createMember()));
        given(weeklyPlanGoalRepository.findByMemberIdAndWeekStartDate(1L, SEOUL_TODAY))
                .willReturn(Optional.empty());
        given(weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(1L, SEOUL_TODAY))
                .willReturn(List.of());

        WeeklyPlanResponse response = weeklyPlanService.findMine(1L, null);

        assertThat(response.weekStartDate()).isEqualTo(SEOUL_TODAY);
        then(weeklyPlanGoalRepository).should().findByMemberIdAndWeekStartDate(1L, SEOUL_TODAY);
    }

    @Test
    @DisplayName("월을 지정하지 않으면 서울 기준 이번 달로 조회한다")
    void resolveDefaultMonthInSeoul() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(createMember()));
        given(monthlyPlanGoalRepository.findByMemberIdAndMonth(1L, SEOUL_MONTH))
                .willReturn(Optional.empty());

        MonthlyPlanGoalResponse response = weeklyPlanService.findMonthlyGoal(1L, null);

        assertThat(response.month()).isEqualTo(SEOUL_MONTH);
        then(monthlyPlanGoalRepository).should().findByMemberIdAndMonth(1L, SEOUL_MONTH);
    }

    @Test
    @DisplayName("스태프는 자기 지점 일반 회원의 주간 계획을 조회한다")
    void staffFindsOwnBranchMemberPlan() {
        Member staff = createMember(9L, 2L, MemberRole.STAFF);
        Member member = createMember(1L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(9L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(weeklyPlanGoalRepository.findByMemberIdAndWeekStartDate(1L, SEOUL_TODAY))
                .willReturn(Optional.empty());
        given(weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(1L, SEOUL_TODAY))
                .willReturn(List.of());

        WeeklyPlanResponse response = weeklyPlanService.findForManager(9L, 1L, null);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("스태프는 다른 지점 회원의 주간 계획을 조회할 수 없다")
    void rejectStaffFindingCrossBranchMemberPlan() {
        Member staff = createMember(9L, 2L, MemberRole.STAFF);
        Member member = createMember(1L, 3L, MemberRole.MEMBER);
        given(memberRepository.findById(9L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> weeklyPlanService.findForManager(9L, 1L, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("스태프는 관리자 역할의 주간 계획을 조회할 수 없다")
    void rejectStaffFindingPrivilegedTargetPlan() {
        Member staff = createMember(9L, 2L, MemberRole.STAFF);
        Member targetStaff = createMember(1L, 2L, MemberRole.STAFF);
        given(memberRepository.findById(9L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetStaff));

        assertThatThrownBy(() -> weeklyPlanService.findForManager(9L, 1L, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원은 관리자용 주간 계획 조회를 사용할 수 없다")
    void rejectMemberUsingManagerPlanLookup() {
        Member member = createMember(9L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(9L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> weeklyPlanService.findForManager(9L, 1L, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("관리자는 다른 지점 스태프의 주간 계획도 조회할 수 있다")
    void adminFindsCrossBranchStaffPlan() {
        Member admin = createMember(9L, 1L, MemberRole.ADMIN);
        Member targetStaff = createMember(1L, 3L, MemberRole.STAFF);
        given(memberRepository.findById(9L)).willReturn(Optional.of(admin));
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetStaff));
        given(weeklyPlanGoalRepository.findByMemberIdAndWeekStartDate(1L, SEOUL_TODAY))
                .willReturn(Optional.empty());
        given(weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(1L, SEOUL_TODAY))
                .willReturn(List.of());

        WeeklyPlanResponse response = weeklyPlanService.findForManager(9L, 1L, null);

        assertThat(response.branchId()).isEqualTo(3L);
    }

    private Member createMember() {
        return createMember(1L, 2L, MemberRole.MEMBER);
    }

    private Member createMember(Long id, Long branchId, MemberRole role) {
        Member member = new Member(
                branchId,
                "김회원",
                "password123",
                role,
                10,
                LocalDate.of(2026, 8, 1),
                null
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
