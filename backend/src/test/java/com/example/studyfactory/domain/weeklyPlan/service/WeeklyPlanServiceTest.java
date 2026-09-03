package com.example.studyfactory.domain.weeklyPlan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
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

    private Member createMember() {
        Member member = new Member(
                2L,
                "김회원",
                "password123",
                MemberRole.MEMBER,
                10,
                LocalDate.of(2026, 8, 1),
                null
        );
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
