package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveOccurrenceCancellationRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EffectiveLeaveHistoryProjectionTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final LocalDate MONTH_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate MONTH_END = LocalDate.of(2026, 10, 31);
    private static final LocalDate DISTANT_DATE = LocalDate.of(9999, 10, 1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private SpecialLeaveRepository specialLeaveRepository;
    @Mock private FixedLeaveRepository fixedLeaveRepository;
    @Mock private LeaveOccurrenceCancellationRepository cancellationRepository;
    @InjectMocks private EffectiveLeaveService effectiveLeaveService;

    @Test
    @DisplayName("본인 신청 이력의 넓은 날짜 범위는 반복휴무를 조회하거나 투영하지 않는다")
    void memberRequestHistoryDoesNotProjectRecurringRules() {
        LeaveRequest cancelled = request(10L, MONTH_START, LeaveType.MORNING);
        LeaveRequest partial = request(11L, DISTANT_DATE, LeaveType.AFTERNOON);
        given(leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                MEMBER_ID, MONTH_START, DISTANT_DATE)).willReturn(List.of(cancelled, partial));
        given(cancellationRepository.findByMemberIdAndLeaveDateBetween(MEMBER_ID, MONTH_START, DISTANT_DATE))
                .willReturn(List.of(
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 10L, MONTH_START, 1),
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 10L, MONTH_START, 2),
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 10L, MONTH_START, 3),
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 10L, MONTH_START, 4),
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 11L, DISTANT_DATE, 4)));

        List<EffectiveLeaveOccurrence> result = effectiveLeaveService.findMemberRequestOccurrences(
                MEMBER_ID, MONTH_START, DISTANT_DATE);

        assertThat(result).singleElement().satisfies(leave -> {
            assertThat(leave.sourceId()).isEqualTo(11L);
            assertThat(leave.date()).isEqualTo(DISTANT_DATE);
            assertThat(leave.slots()).containsExactlyInAnyOrder(5, 6, 7);
        });
        verifyNoInteractions(fixedLeaveRepository, specialLeaveRepository);
    }

    @Test
    @DisplayName("계획 이력은 모든 직접 기록과 해당 규칙의 생성일만 보존하고 날짜 사이를 투영하지 않는다")
    void memberPlanProjectsOnlyVisibleMonthAndEachRulesMaterializedDates() {
        LocalDate pastDate = LocalDate.of(2026, 9, 26);
        FixedLeave work = spy(rule(50L, "알바"));
        FixedLeave school = spy(rule(51L, "학교"));
        LeaveRequest futureRequest = request(10L, DISTANT_DATE, LeaveType.FULL);
        SpecialLeave direct = special(20L, DISTANT_DATE.plusDays(1), null, "아픔");
        SpecialLeave pastCache = special(21L, pastDate, work.getId(), "알바");
        SpecialLeave futureCache = special(22L, DISTANT_DATE, work.getId(), "알바");
        given(leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(MEMBER_ID))
                .willReturn(List.of(futureRequest));
        given(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(MEMBER_ID))
                .willReturn(List.of(direct, futureCache, pastCache));
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(MEMBER_ID)).willReturn(List.of(work, school));
        given(cancellationRepository.findByMemberIdAndLeaveDateBetween(MEMBER_ID, pastDate, DISTANT_DATE.plusDays(1)))
                .willReturn(List.of(
                        cancellation(LeaveSourceType.MEMBER_LEAVE, 10L, DISTANT_DATE, 1),
                        cancellation(LeaveSourceType.FIXED_LEAVE, 50L, pastDate, 1)));

        List<EffectiveLeaveOccurrence> result = effectiveLeaveService.findMemberHistory(MEMBER_ID, MONTH_START, MONTH_END);

        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.MEMBER_LEAVE)
                .singleElement().satisfies(leave -> assertThat(leave.slots()).containsExactlyInAnyOrder(2, 3, 4, 5, 6, 7));
        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.SPECIAL_LEAVE)
                .singleElement().satisfies(leave -> {
                    assertThat(leave.sourceId()).isEqualTo(20L);
                    assertThat(leave.date()).isEqualTo(DISTANT_DATE.plusDays(1));
                });
        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.FIXED_LEAVE && leave.sourceId().equals(50L))
                .hasSize(7).anySatisfy(leave -> {
                    assertThat(leave.date()).isEqualTo(pastDate);
                    assertThat(leave.slots()).containsExactly(2);
                }).anySatisfy(leave -> assertThat(leave.date()).isEqualTo(DISTANT_DATE));
        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.FIXED_LEAVE && leave.sourceId().equals(51L))
                .hasSize(5).allSatisfy(leave -> assertThat(leave.date()).isBetween(MONTH_START, MONTH_END));
        verify(work, times(33)).appliesOn(any(LocalDate.class));
        verify(school, times(31)).appliesOn(any(LocalDate.class));
    }

    @Test
    @DisplayName("관리자 특별휴무 이력은 실제 저장된 발생일만 투영하고 다른 반복규칙은 추가하지 않는다")
    void managerHistoryProjectsOnlyStoredDatesAndPreservesSourceCancellation() {
        LocalDate pastDate = LocalDate.of(2026, 9, 26);
        FixedLeave work = spy(rule(50L, "알바"));
        FixedLeave unrelated = spy(rule(51L, "학교"));
        SpecialLeave pastCache = special(20L, pastDate, work.getId(), "알바");
        SpecialLeave futureCache = special(21L, DISTANT_DATE, work.getId(), "알바");
        SpecialLeave direct = special(22L, DISTANT_DATE.plusDays(1), null, "아픔");
        given(specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(MEMBER_ID))
                .willReturn(List.of(direct, futureCache, pastCache));
        given(fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(MEMBER_ID)).willReturn(List.of(work, unrelated));
        given(cancellationRepository.findByMemberIdAndLeaveDateBetween(MEMBER_ID, pastDate, DISTANT_DATE.plusDays(1)))
                .willReturn(List.of(
                        cancellation(LeaveSourceType.FIXED_LEAVE, 50L, pastDate, 1),
                        cancellation(LeaveSourceType.SPECIAL_LEAVE, 22L, DISTANT_DATE.plusDays(1), 2)));

        List<EffectiveLeaveOccurrence> result = effectiveLeaveService.findMemberSpecialHistory(MEMBER_ID);

        assertThat(result).hasSize(3);
        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.FIXED_LEAVE)
                .hasSize(2).allSatisfy(leave -> assertThat(leave.sourceId()).isEqualTo(50L))
                .anySatisfy(leave -> {
                    assertThat(leave.date()).isEqualTo(pastDate);
                    assertThat(leave.slots()).containsExactly(2);
                }).anySatisfy(leave -> assertThat(leave.date()).isEqualTo(DISTANT_DATE));
        assertThat(result).filteredOn(leave -> leave.sourceType() == LeaveSourceType.SPECIAL_LEAVE)
                .singleElement().satisfies(leave -> {
                    assertThat(leave.sourceId()).isEqualTo(22L);
                    assertThat(leave.slots()).containsExactly(1);
                });
        verify(work, times(2)).appliesOn(any(LocalDate.class));
        verify(unrelated, times(0)).appliesOn(any(LocalDate.class));
        verifyNoInteractions(leaveRequestRepository);
    }

    private LeaveRequest request(Long id, LocalDate date, LeaveType type) {
        LeaveRequest request = new LeaveRequest(MEMBER_ID, BRANCH_ID, date, type);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private FixedLeave rule(Long id, String reason) {
        FixedLeave rule = new FixedLeave(MEMBER_ID, BRANCH_ID, DayOfWeek.SATURDAY, "1,2", reason, true);
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }

    private SpecialLeave special(Long id, LocalDate date, Long ruleId, String reason) {
        SpecialLeave leave = new SpecialLeave(MEMBER_ID, BRANCH_ID, date, "1,2", reason, null, ruleId != null, 9L, ruleId);
        ReflectionTestUtils.setField(leave, "id", id);
        return leave;
    }

    private LeaveOccurrenceCancellation cancellation(LeaveSourceType source, Long sourceId, LocalDate date, int slot) {
        return new LeaveOccurrenceCancellation(MEMBER_ID, BRANCH_ID, source, sourceId, date, slot, 9L);
    }
}
