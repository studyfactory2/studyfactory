package com.example.studyfactory.domain.attendance.service;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.DailyAttendanceBoardResponse;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final int MAX_SEAT_NUMBER = 60;
    private static final int SLOT_COUNT = 7;
    private static final String EMPTY_STATUS = "X";
    private static final String PRESENT_STATUS = "O";

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final SpecialLeaveRepository specialLeaveRepository;

    @Transactional(readOnly = true)
    public DailyAttendanceBoardResponse findDailyBoard(Long currentMemberId, LocalDate date, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        LocalDate targetDate = resolveDate(date);
        Long targetBranchId = resolveBranchId(currentMember, branchId);
        List<Member> members = memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(targetBranchId);
        Map<Integer, Member> membersBySeat = toMembersBySeat(members);
        Map<Long, List<String>> statusesByMemberId = initializeStatuses(members);

        applyAttendances(statusesByMemberId, targetBranchId, targetDate);
        applyLeaveRequests(statusesByMemberId, targetBranchId, targetDate);
        applyFixedLeaves(statusesByMemberId, targetBranchId, targetDate);
        applySpecialLeaves(statusesByMemberId, targetBranchId, targetDate);

        return new DailyAttendanceBoardResponse(targetDate, toRows(members, membersBySeat, statusesByMemberId));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private LocalDate resolveDate(LocalDate date) {
        if (date == null) {
            return LocalDate.now();
        }

        return date;
    }

    private Long resolveBranchId(Member currentMember, Long branchId) {
        if (branchId == null) {
            return currentMember.getBranchId();
        }

        return branchId;
    }

    private Map<Integer, Member> toMembersBySeat(List<Member> members) {
        Map<Integer, Member> membersBySeat = new HashMap<>();
        for (Member member : members) {
            if (isUnassignedSeat(member)) {
                continue;
            }
            membersBySeat.putIfAbsent(member.getSeatNumber(), member);
        }

        return membersBySeat;
    }

    private Map<Long, List<String>> initializeStatuses(List<Member> members) {
        Map<Long, List<String>> statusesByMemberId = new HashMap<>();
        for (Member member : members) {
            statusesByMemberId.put(member.getId(), emptySlots());
        }

        return statusesByMemberId;
    }

    private List<String> emptySlots() {
        List<String> slots = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            slots.add(EMPTY_STATUS);
        }

        return slots;
    }

    private void applyAttendances(Map<Long, List<String>> statusesByMemberId, Long branchId, LocalDate date) {
        for (Attendance attendance : attendanceRepository.findDailyBoardAttendances(branchId, date)) {
            List<String> statuses = statusesByMemberId.get(attendance.getMemberId());
            if (statuses == null) {
                continue;
            }
            setStatus(statuses, attendance.getSlot(), PRESENT_STATUS);
        }
    }

    private void applyLeaveRequests(Map<Long, List<String>> statusesByMemberId, Long branchId, LocalDate date) {
        for (LeaveRequest leaveRequest : leaveRequestRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(branchId, date)) {
            List<String> statuses = statusesByMemberId.get(leaveRequest.getMemberId());
            if (statuses == null) {
                continue;
            }
            for (Integer slot : toLeaveSlots(leaveRequest.getLeaveType())) {
                setStatus(statuses, slot, toLeaveTypeLabel(leaveRequest.getLeaveType()));
            }
        }
    }

    private void applyFixedLeaves(Map<Long, List<String>> statusesByMemberId, Long branchId, LocalDate date) {
        for (FixedLeave fixedLeave : fixedLeaveRepository.findByBranchIdAndActiveTrueOrderByCreatedAtAsc(branchId)) {
            if (fixedLeave.getDayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            List<String> statuses = statusesByMemberId.get(fixedLeave.getMemberId());
            if (statuses == null) {
                continue;
            }
            for (Integer slot : parseSlots(fixedLeave.getSlots())) {
                setStatus(statuses, slot, fixedLeave.getReason());
            }
        }
    }

    private void applySpecialLeaves(Map<Long, List<String>> statusesByMemberId, Long branchId, LocalDate date) {
        for (SpecialLeave specialLeave : specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(branchId, date)) {
            List<String> statuses = statusesByMemberId.get(specialLeave.getMemberId());
            if (statuses == null) {
                continue;
            }
            for (Integer slot : parseSlots(specialLeave.getSlots())) {
                setStatus(statuses, slot, toSpecialLeaveLabel(specialLeave));
            }
        }
    }

    private void setStatus(List<String> statuses, Integer slot, String status) {
        if (slot < 1 || slot > SLOT_COUNT) {
            return;
        }
        statuses.set(slot - 1, status);
    }

    private List<Integer> toLeaveSlots(LeaveType leaveType) {
        if (leaveType == LeaveType.MORNING) {
            return List.of(1, 2, 3, 4);
        }
        if (leaveType == LeaveType.AFTERNOON) {
            return List.of(4, 5, 6, 7);
        }

        return List.of(1, 2, 3, 4, 5, 6, 7);
    }

    private List<Integer> parseSlots(String slots) {
        return Arrays.stream(slots.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::parseInt)
                .toList();
    }

    private String toLeaveTypeLabel(LeaveType leaveType) {
        if (leaveType == LeaveType.FULL) {
            return "월차";
        }
        if (leaveType == LeaveType.MORNING) {
            return "오전";
        }

        return "오후";
    }

    private String toSpecialLeaveLabel(SpecialLeave specialLeave) {
        if ("기타".equals(specialLeave.getReason()) && specialLeave.getCustomReason() != null) {
            return specialLeave.getCustomReason();
        }

        return specialLeave.getReason();
    }

    private List<AttendanceBoardRowResponse> toRows(
            List<Member> members,
            Map<Integer, Member> membersBySeat,
            Map<Long, List<String>> statusesByMemberId
    ) {
        List<AttendanceBoardRowResponse> rows = new ArrayList<>();
        int lastSeatNumber = Math.max(MAX_SEAT_NUMBER, membersBySeat.keySet().stream().max(Comparator.naturalOrder()).orElse(0));
        for (int seatNumber = 1; seatNumber <= lastSeatNumber; seatNumber++) {
            Member member = membersBySeat.get(seatNumber);
            if (member == null) {
                rows.add(new AttendanceBoardRowResponse(seatNumber, "공석", emptySlots()));
                continue;
            }
            rows.add(new AttendanceBoardRowResponse(seatNumber, member.getName(), statusesByMemberId.get(member.getId())));
        }
        members.stream()
                .filter(this::isUnassignedSeat)
                .forEach(member -> rows.add(new AttendanceBoardRowResponse(null, member.getName(), statusesByMemberId.get(member.getId()))));

        return rows;
    }

    private boolean isUnassignedSeat(Member member) {
        return member.getSeatNumber() == null || member.getSeatNumber() < 1;
    }
}
