package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Active leave shown to users; stored leave, allowance and study calculations stay independent. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceLeaveProjection {
    private final AttendanceRepository attendanceRepository;

    public List<EffectiveLeaveOccurrence> withoutPresentPeriods(List<EffectiveLeaveOccurrence> leaves) {
        if (leaves.isEmpty()) return List.of();
        LocalDate start = leaves.stream().map(EffectiveLeaveOccurrence::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = leaves.stream().map(EffectiveLeaveOccurrence::date).max(LocalDate::compareTo).orElseThrow();
        List<Long> memberIds = leaves.stream().map(EffectiveLeaveOccurrence::memberId).distinct().toList();
        Map<MemberBranchDate, Set<Integer>> presentSlots = new HashMap<>();
        attendanceRepository.findPresentOverridesForMembers(
                memberIds, start, end, Attendance.FIXED_LEAVE_CANCELLATION_MARKER).forEach(attendance ->
                presentSlots.computeIfAbsent(new MemberBranchDate(attendance.getMemberId(), attendance.getBranchId(), attendance.getAttendanceDate()),
                        ignored -> new HashSet<>()).add(attendance.getSlot()));
        // No cancellation is written: removing O automatically reveals the still-valid leave,
        // while explicit source cancellations have already been applied by EffectiveLeaveService.
        return leaves.stream().map(leave -> {
                    Set<Integer> slots = new TreeSet<>(leave.slots());
                    slots.removeAll(presentSlots.getOrDefault(new MemberBranchDate(leave.memberId(), leave.branchId(), leave.date()), Set.of()));
                    return new EffectiveLeaveOccurrence(leave.memberId(), leave.branchId(), leave.sourceType(),
                            leave.sourceId(), leave.date(), slots, leave.reason(), leave.leaveType(), leave.createdAt());
                })
                .filter(leave -> !leave.slots().isEmpty())
                .toList();
    }

    private record MemberBranchDate(Long memberId, Long branchId, LocalDate date) {}
}
