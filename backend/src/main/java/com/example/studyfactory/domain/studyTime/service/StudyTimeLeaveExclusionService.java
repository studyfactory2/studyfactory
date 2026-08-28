package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyTimeLeaveExclusionService {

    private static final Map<Integer, StudyPeriod> PERIOD_BY_SLOT = Arrays.stream(StudyPeriod.values())
            .collect(Collectors.toUnmodifiableMap(StudyPeriod::getPeriodNumber, period -> period));

    private final LeaveRequestRepository leaveRequestRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final AttendanceRepository attendanceRepository;

    @Transactional(readOnly = true)
    public Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            Long memberId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validate(memberId, fromDate, toDate);

        return findExcludedPeriods(
                fromDate,
                toDate,
                leaveRequestRepository
                        .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                                memberId, fromDate, toDate
                        ),
                fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(memberId),
                specialLeaveRepository
                        .findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                                memberId, fromDate, toDate
                        ),
                attendanceRepository.findByMemberIdAndAttendanceDateBetween(memberId, fromDate, toDate)
        );
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            Long memberId,
            Long branchId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validate(memberId, fromDate, toDate);
        Objects.requireNonNull(branchId, "branchId must not be null");

        return findExcludedPeriods(
                fromDate,
                toDate,
                leaveRequestRepository
                        .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                                memberId, branchId, fromDate, toDate
                        ),
                fixedLeaveRepository
                        .findByMemberIdAndBranchIdAndActiveTrueOrderByCreatedAtAsc(memberId, branchId),
                specialLeaveRepository
                        .findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(
                                memberId, branchId, fromDate, toDate
                        ),
                attendanceRepository.findByMemberIdAndBranchIdAndAttendanceDateBetween(
                        memberId, branchId, fromDate, toDate
                )
        );
    }

    private Map<LocalDate, Set<StudyPeriod>> findExcludedPeriods(
            LocalDate fromDate,
            LocalDate toDate,
            List<LeaveRequest> leaveRequests,
            List<FixedLeave> fixedLeaves,
            List<SpecialLeave> specialLeaves,
            List<Attendance> attendances
    ) {
        Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods = initializeDates(fromDate, toDate);
        leaveRequests.forEach(leaveRequest -> applyLeaveRequest(excludedPeriods, leaveRequest));
        fixedLeaves.forEach(fixedLeave -> applyFixedLeave(excludedPeriods, fixedLeave));
        specialLeaves.forEach(specialLeave -> applySpecialLeave(excludedPeriods, specialLeave));
        attendances.forEach(attendance -> applyAttendanceOverride(excludedPeriods, attendance));

        return immutableCopy(excludedPeriods);
    }

    private void validate(Long memberId, LocalDate fromDate, LocalDate toDate) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    private Map<LocalDate, EnumSet<StudyPeriod>> initializeDates(LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, EnumSet<StudyPeriod>> periodsByDate = new LinkedHashMap<>();
        LocalDate date = fromDate;
        while (true) {
            periodsByDate.put(date, EnumSet.noneOf(StudyPeriod.class));
            if (date.equals(toDate)) {
                return periodsByDate;
            }
            date = date.plusDays(1);
        }
    }

    private void applyLeaveRequest(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods,
            LeaveRequest leaveRequest
    ) {
        EnumSet<StudyPeriod> periods = excludedPeriods.get(leaveRequest.getLeaveDate());
        if (periods == null) {
            return;
        }

        periods.addAll(toLeavePeriods(leaveRequest.getLeaveType()));
    }

    private void applyFixedLeave(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods,
            FixedLeave fixedLeave
    ) {
        Set<StudyPeriod> leavePeriods = parsePeriods(fixedLeave.getSlots());
        if (leavePeriods.isEmpty()) {
            return;
        }

        excludedPeriods.forEach((date, periods) -> {
            if (date.getDayOfWeek() == fixedLeave.getDayOfWeek()) {
                periods.addAll(leavePeriods);
            }
        });
    }

    private void applySpecialLeave(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods,
            SpecialLeave specialLeave
    ) {
        EnumSet<StudyPeriod> periods = excludedPeriods.get(specialLeave.getLeaveDate());
        if (periods == null) {
            return;
        }

        periods.addAll(parsePeriods(specialLeave.getSlots()));
    }

    private void applyAttendanceOverride(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods,
            Attendance attendance
    ) {
        EnumSet<StudyPeriod> periods = excludedPeriods.get(attendance.getAttendanceDate());
        StudyPeriod period = PERIOD_BY_SLOT.get(attendance.getSlot());
        if (periods != null && period != null) {
            periods.remove(period);
        }
    }

    private Set<StudyPeriod> toLeavePeriods(LeaveType leaveType) {
        if (leaveType == LeaveType.MORNING) {
            return periodsBetween(1, 4);
        }
        if (leaveType == LeaveType.AFTERNOON) {
            return periodsBetween(4, 7);
        }

        return EnumSet.allOf(StudyPeriod.class);
    }

    private Set<StudyPeriod> periodsBetween(int firstSlot, int lastSlot) {
        EnumSet<StudyPeriod> periods = EnumSet.noneOf(StudyPeriod.class);
        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            periods.add(PERIOD_BY_SLOT.get(slot));
        }
        return periods;
    }

    private Set<StudyPeriod> parsePeriods(String slots) {
        EnumSet<StudyPeriod> periods = EnumSet.noneOf(StudyPeriod.class);
        if (slots == null || slots.isBlank()) {
            return periods;
        }

        for (String value : slots.split(",")) {
            try {
                StudyPeriod period = PERIOD_BY_SLOT.get(Integer.parseInt(value.trim()));
                if (period != null) {
                    periods.add(period);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy values while retaining valid slots from the same row.
            }
        }
        return periods;
    }

    private Map<LocalDate, Set<StudyPeriod>> immutableCopy(
            Map<LocalDate, EnumSet<StudyPeriod>> excludedPeriods
    ) {
        Map<LocalDate, Set<StudyPeriod>> result = new LinkedHashMap<>();
        excludedPeriods.forEach((date, periods) -> result.put(
                date,
                periods.isEmpty()
                        ? Set.of()
                        : Collections.unmodifiableSet(EnumSet.copyOf(periods))
        ));
        return Collections.unmodifiableMap(result);
    }
}
