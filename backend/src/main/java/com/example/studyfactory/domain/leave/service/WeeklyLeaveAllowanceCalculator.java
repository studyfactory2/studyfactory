package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import com.example.studyfactory.domain.leave.dto.WeeklyLeaveAllowanceResponse;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Pure projection of effective member requests; does not reject or change any leave or attendance. */
@Component
public class WeeklyLeaveAllowanceCalculator {
    public static final int ALLOWANCE_UNITS = 3;

    private static final Comparator<EffectiveLeaveOccurrence> SUBMISSION_ORDER =
            Comparator.comparing(EffectiveLeaveOccurrence::createdAt)
                    .thenComparing(EffectiveLeaveOccurrence::sourceId);

    /**
     * Callers supply complete weeks of cancellation-filtered occurrences. A request with any
     * remaining period uses its original type's units, independently of branch or attendance.
     */
    public Map<Long, WeeklyLeaveAllowanceResponse> calculate(List<EffectiveLeaveOccurrence> occurrences) {
        Objects.requireNonNull(occurrences, "occurrences must not be null");
        Map<MemberWeek, List<EffectiveLeaveOccurrence>> requestsByWeek = new HashMap<>();
        for (EffectiveLeaveOccurrence occurrence : occurrences) {
            if (occurrence.sourceType() != LeaveSourceType.MEMBER_LEAVE || occurrence.slots().isEmpty()) {
                continue;
            }
            Objects.requireNonNull(occurrence.sourceId(), "member request must have an id");
            Objects.requireNonNull(occurrence.createdAt(), "member request must have a creation time");
            Objects.requireNonNull(occurrence.leaveType(), "member request must have a leave type");
            MemberWeek key = new MemberWeek(
                    Objects.requireNonNull(occurrence.memberId(), "member request must have a member id"),
                    weekStart(occurrence.date()));
            requestsByWeek.computeIfAbsent(key, ignored -> new ArrayList<>()).add(occurrence);
        }

        Map<Long, WeeklyLeaveAllowanceResponse> result = new HashMap<>();
        requestsByWeek.forEach((week, requests) -> {
            requests.sort(SUBMISSION_ORDER);
            int totalUnits = requests.stream().mapToInt(request -> unitsFor(request.leaveType())).sum();
            int usedUnits = 0;
            for (EffectiveLeaveOccurrence request : requests) {
                int requestUnits = unitsFor(request.leaveType());
                int usedUnitsAfter = usedUnits + requestUnits;
                result.put(request.sourceId(), new WeeklyLeaveAllowanceResponse(
                        week.start(), week.start().plusDays(6), ALLOWANCE_UNITS,
                        requestUnits, usedUnits, usedUnitsAfter, totalUnits,
                        usedUnitsAfter > ALLOWANCE_UNITS));
                usedUnits = usedUnitsAfter;
            }
        });
        return Map.copyOf(result);
    }

    public static LocalDate weekStart(LocalDate leaveDate) {
        return Objects.requireNonNull(leaveDate, "leaveDate must not be null")
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int unitsFor(LeaveType type) {
        return switch (type) {
            case FULL -> 2;
            case MORNING, AFTERNOON -> 1;
        };
    }

    private record MemberWeek(Long memberId, LocalDate start) {}
}
