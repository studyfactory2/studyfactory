package com.example.studyfactory.domain.leave.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import com.example.studyfactory.domain.leave.dto.WeeklyLeaveAllowanceResponse;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeeklyLeaveAllowanceCalculatorTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 10, 12, 9, 0);

    private final WeeklyLeaveAllowanceCalculator calculator = new WeeklyLeaveAllowanceCalculator();

    @Test
    void emptyOccurrencesHaveNoAllowanceMetadata() {
        assertThat(calculator.calculate(List.of())).isEmpty();
    }

    @Test
    void oneFullDayAndOneHalfDayUseTheAllowanceWithoutExcess() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, MONDAY, LeaveType.FULL),
                request(2L, MONDAY.plusDays(1), LeaveType.MORNING)));

        assertState(result.get(1L), MONDAY, 2, 0, 2, 3, false);
        assertState(result.get(2L), MONDAY, 1, 2, 3, 3, false);
    }

    @Test
    void threeHalfDaysAreWithinAllowanceEvenOnTheSameDate() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, MONDAY, LeaveType.MORNING),
                request(2L, MONDAY, LeaveType.AFTERNOON),
                request(3L, MONDAY, LeaveType.MORNING)));

        assertState(result.get(1L), MONDAY, 1, 0, 1, 3, false);
        assertState(result.get(2L), MONDAY, 1, 1, 2, 3, false);
        assertState(result.get(3L), MONDAY, 1, 2, 3, 3, false);
    }

    @Test
    void secondFullDayIsEntirelyFlaggedWhenItCrossesTheAllowance() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, MONDAY, LeaveType.FULL),
                request(2L, MONDAY.plusDays(1), LeaveType.FULL)));

        assertState(result.get(1L), MONDAY, 2, 0, 2, 4, false);
        assertState(result.get(2L), MONDAY, 2, 2, 4, 4, true);
    }

    @Test
    void fourthHalfDayIsFlaggedWithoutChangingEarlierRequests() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, MONDAY, LeaveType.MORNING),
                request(2L, MONDAY, LeaveType.AFTERNOON),
                request(3L, MONDAY, LeaveType.MORNING),
                request(4L, MONDAY, LeaveType.AFTERNOON)));

        assertState(result.get(1L), MONDAY, 1, 0, 1, 4, false);
        assertState(result.get(2L), MONDAY, 1, 1, 2, 4, false);
        assertState(result.get(3L), MONDAY, 1, 2, 3, 4, false);
        assertState(result.get(4L), MONDAY, 1, 3, 4, 4, true);
    }

    @Test
    void submissionOrderControlsExcessRatherThanLeaveDateInputOrderOrRequestId() {
        EffectiveLeaveOccurrence firstSubmission = request(20L, 1L, 10L, MONDAY.plusDays(4),
                LeaveType.FULL, SUBMITTED_AT, Set.of(1));
        EffectiveLeaveOccurrence laterSubmission = request(10L, 1L, 10L, MONDAY,
                LeaveType.FULL, SUBMITTED_AT.plusMinutes(1), Set.of(1));

        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(
                List.of(laterSubmission, firstSubmission));

        assertState(result.get(20L), MONDAY, 2, 0, 2, 4, false);
        assertState(result.get(10L), MONDAY, 2, 2, 4, 4, true);
    }

    @Test
    void requestIdBreaksEqualCreationTimeTiesDeterministically() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(30L, 1L, 10L, MONDAY, LeaveType.AFTERNOON, SUBMITTED_AT, Set.of(7)),
                request(20L, 1L, 10L, MONDAY, LeaveType.FULL, SUBMITTED_AT, Set.of(1)),
                request(10L, 1L, 10L, MONDAY, LeaveType.MORNING, SUBMITTED_AT, Set.of(1))));

        assertState(result.get(10L), MONDAY, 1, 0, 1, 4, false);
        assertState(result.get(20L), MONDAY, 2, 1, 3, 4, false);
        assertState(result.get(30L), MONDAY, 1, 3, 4, 4, true);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-26, 2026-11-01, 2026-11-02",
            "2026-12-28, 2027-01-03, 2027-01-04"
    })
    void leaveDateWeeksSpanMonthAndYearBoundariesAndResetOnMonday(
            LocalDate monday, LocalDate sunday, LocalDate nextMonday) {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, monday, LeaveType.FULL),
                request(2L, sunday, LeaveType.AFTERNOON),
                request(3L, nextMonday, LeaveType.FULL)));

        assertThat(WeeklyLeaveAllowanceCalculator.weekStart(sunday)).isEqualTo(monday);
        assertThat(WeeklyLeaveAllowanceCalculator.weekStart(nextMonday)).isEqualTo(nextMonday);
        assertState(result.get(1L), monday, 2, 0, 2, 3, false);
        assertState(result.get(2L), monday, 1, 2, 3, 3, false);
        assertState(result.get(3L), nextMonday, 2, 0, 2, 2, false);
    }

    @Test
    void usageIsIsolatedByMemberButCombinedAcrossBranches() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, 1L, 10L, MONDAY, LeaveType.FULL, SUBMITTED_AT, Set.of(1)),
                request(2L, 1L, 20L, MONDAY, LeaveType.FULL, SUBMITTED_AT.plusMinutes(1), Set.of(1)),
                request(3L, 2L, 10L, MONDAY, LeaveType.FULL, SUBMITTED_AT.plusMinutes(2), Set.of(1))));

        assertState(result.get(1L), MONDAY, 2, 0, 2, 4, false);
        assertState(result.get(2L), MONDAY, 2, 2, 4, 4, true);
        assertState(result.get(3L), MONDAY, 2, 0, 2, 2, false);
    }

    @Test
    void officeAndRecurringLeavesNeverCountOrCollideWithMemberRequestIds() {
        EffectiveLeaveOccurrence recurring = new EffectiveLeaveOccurrence(1L, 10L, LeaveSourceType.FIXED_LEAVE,
                1L, MONDAY, Set.of(1, 2, 3, 4, 5, 6, 7), "알바", null, SUBMITTED_AT);
        EffectiveLeaveOccurrence office = new EffectiveLeaveOccurrence(1L, 10L, LeaveSourceType.SPECIAL_LEAVE,
                2L, MONDAY, Set.of(1, 2, 3, 4, 5, 6, 7), "학교", null, SUBMITTED_AT);

        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                recurring, request(1L, MONDAY, LeaveType.MORNING), office));

        assertThat(result).containsOnlyKeys(1L);
        assertState(result.get(1L), MONDAY, 1, 0, 1, 1, false);
    }

    @Test
    void partialRequestsRetainOriginalUnitsAndEmptyRequestsDoNotCount() {
        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(List.of(
                request(1L, 1L, 10L, MONDAY, LeaveType.FULL, SUBMITTED_AT, Set.of(7)),
                request(2L, 1L, 10L, MONDAY, LeaveType.MORNING, SUBMITTED_AT.plusMinutes(1), Set.of(4)),
                request(3L, 1L, 10L, MONDAY, LeaveType.AFTERNOON, SUBMITTED_AT.plusMinutes(2), Set.of(7)),
                request(4L, 1L, 10L, MONDAY, LeaveType.FULL, SUBMITTED_AT.minusMinutes(1), Set.of())));

        assertThat(result).containsOnlyKeys(1L, 2L, 3L);
        assertState(result.get(1L), MONDAY, 2, 0, 2, 4, false);
        assertState(result.get(2L), MONDAY, 1, 2, 3, 4, false);
        assertState(result.get(3L), MONDAY, 1, 3, 4, 4, true);
    }

    @Test
    void fullyCancellingEarlierRequestRecalculatesRemainingRequests() {
        EffectiveLeaveOccurrence first = request(1L, MONDAY, LeaveType.FULL);
        EffectiveLeaveOccurrence second = request(2L, MONDAY, LeaveType.FULL);
        Map<Long, WeeklyLeaveAllowanceResponse> before = calculator.calculate(List.of(first, second));
        Map<Long, WeeklyLeaveAllowanceResponse> after = calculator.calculate(List.of(second));

        assertThat(before.get(2L).excess()).isTrue();
        assertThat(after).containsOnlyKeys(2L);
        assertState(after.get(2L), MONDAY, 2, 0, 2, 2, false);
    }

    @Test
    void calculationDoesNotMutateInputOrderOrEffectivePeriods() {
        EffectiveLeaveOccurrence first = request(1L, MONDAY, LeaveType.FULL);
        EffectiveLeaveOccurrence second = request(2L, MONDAY, LeaveType.AFTERNOON);
        List<EffectiveLeaveOccurrence> input = new ArrayList<>(List.of(second, first));
        List<EffectiveLeaveOccurrence> snapshot = List.copyOf(input);

        Map<Long, WeeklyLeaveAllowanceResponse> result = calculator.calculate(input);

        assertThat(input).containsExactlyElementsOf(snapshot);
        assertThat(first.slots()).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
        assertThat(second.slots()).containsExactlyInAnyOrder(4, 5, 6, 7);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unsavedRequestsCannotSilentlyReceiveAnArbitrarySubmissionOrder() {
        EffectiveLeaveOccurrence missingCreationTime = request(1L, 1L, 10L, MONDAY,
                LeaveType.MORNING, null, Set.of(1));
        EffectiveLeaveOccurrence missingId = request(null, 1L, 10L, MONDAY,
                LeaveType.MORNING, SUBMITTED_AT, Set.of(1));

        assertThatThrownBy(() -> calculator.calculate(List.of(missingCreationTime)))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("creation time");
        assertThatThrownBy(() -> calculator.calculate(List.of(missingId)))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("id");
    }

    private EffectiveLeaveOccurrence request(Long id, LocalDate date, LeaveType type) {
        return request(id, 1L, 10L, date, type, SUBMITTED_AT.plusMinutes(id),
                EffectiveLeaveService.slotsForType(type));
    }

    private EffectiveLeaveOccurrence request(Long id, Long memberId, Long branchId, LocalDate date,
            LeaveType type, LocalDateTime createdAt, Set<Integer> slots) {
        return new EffectiveLeaveOccurrence(memberId, branchId, LeaveSourceType.MEMBER_LEAVE,
                id, date, slots, EffectiveLeaveService.labelForType(type), type, createdAt);
    }

    private void assertState(WeeklyLeaveAllowanceResponse actual, LocalDate weekStart,
            int requestUnits, int before, int after, int total, boolean excess) {
        assertThat(actual).isEqualTo(new WeeklyLeaveAllowanceResponse(
                weekStart, weekStart.plusDays(6), 3, requestUnits, before, after, total, excess));
    }
}
