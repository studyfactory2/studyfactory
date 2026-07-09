package com.example.studyfactory.domain.attendance.service;

import com.example.studyfactory.domain.attendance.dto.AttendanceBoardRowResponse;
import com.example.studyfactory.domain.attendance.dto.AttendanceDailyResetRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateRequest;
import com.example.studyfactory.domain.attendance.dto.AttendanceSlotStatusUpdateType;
import com.example.studyfactory.domain.attendance.dto.DailyAttendanceBoardResponse;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceDailyInitialization;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import com.example.studyfactory.domain.attendance.repository.AttendanceDailyInitializationRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
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
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final int MAX_SEAT_NUMBER = 102;
    private static final int SLOT_COUNT = 7;
    private static final String EMPTY_STATUS = "X";
    private static final String PRESENT_STATUS = "O";
    private static final String PRESENT_STATUS_TYPE_NAME = "출석";

    private final AttendanceRepository attendanceRepository;
    private final AttendanceDailyInitializationRepository attendanceDailyInitializationRepository;
    private final AttendanceStatusTypeRepository attendanceStatusTypeRepository;
    private final MemberRepository memberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final CertificationRepository certificationRepository;

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
        Set<Long> initializedMemberIds = findInitializedMemberIds(targetBranchId, targetDate);

        return new DailyAttendanceBoardResponse(targetDate, toRows(members, membersBySeat, statusesByMemberId, initializedMemberIds));
    }

    @Transactional
    public void updateSlotStatus(Long currentMemberId, AttendanceSlotStatusUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member member = findMember(request.memberId());
        if (!currentMember.getBranchId().equals(member.getBranchId()) && !currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }

        clearSlotStatus(member.getId(), request.date(), request.slot());
        if (request.status() == AttendanceSlotStatusUpdateType.PRESENT) {
            createPresentAttendance(currentMember, member, request);
        }
        if (request.status() == AttendanceSlotStatusUpdateType.OTHER) {
            createSpecialLeave(currentMember, member, request);
        }
    }

    @Transactional
    public void resetDailyStatus(Long currentMemberId, AttendanceDailyResetRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member member = findMember(request.memberId());
        if (!currentMember.getBranchId().equals(member.getBranchId()) && !currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }

        attendanceRepository.deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDate(member.getId(), request.date());
        leaveRequestRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), request.date())
                .forEach(leaveRequestRepository::delete);
        specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(member.getId(), request.date())
                .forEach(specialLeaveRepository::delete);
        if (!attendanceDailyInitializationRepository.existsByMemberIdAndAttendanceDate(member.getId(), request.date())) {
            attendanceDailyInitializationRepository.save(new AttendanceDailyInitialization(
                    member.getId(), member.getBranchId(), request.date(), currentMember.getId()
            ));
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private void clearSlotStatus(Long memberId, LocalDate date, Integer slot) {
        attendanceRepository.deleteByReferenceInformationMemberIdAndSlotInformationAttendanceDateAndSlotInformationSlot(memberId, date, slot);
        attendanceRepository.flush();
        deleteLeaveRequestsBySlot(memberId, date, slot);
        deleteSpecialLeavesBySlot(memberId, date, slot);
    }

    private void deleteLeaveRequestsBySlot(Long memberId, LocalDate date, Integer slot) {
        for (LeaveRequest leaveRequest : leaveRequestRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(memberId, date)) {
            if (toLeaveSlots(leaveRequest.getLeaveType()).contains(slot)) {
                leaveRequestRepository.delete(leaveRequest);
            }
        }
    }

    private void deleteSpecialLeavesBySlot(Long memberId, LocalDate date, Integer slot) {
        for (SpecialLeave specialLeave : specialLeaveRepository.findByMemberIdAndLeaveDateOrderByCreatedAtAsc(memberId, date)) {
            boolean empty = specialLeave.removeSlot(slot);
            if (empty) {
                specialLeaveRepository.delete(specialLeave);
            }
        }
    }

    private void createPresentAttendance(Member currentMember, Member member, AttendanceSlotStatusUpdateRequest request) {
        AttendanceStatusType statusType = attendanceStatusTypeRepository.findByName(PRESENT_STATUS_TYPE_NAME)
                .orElseGet(() -> attendanceStatusTypeRepository.save(new AttendanceStatusType(PRESENT_STATUS_TYPE_NAME, false)));
        attendanceRepository.save(new Attendance(
                new AttendanceReferenceInformation(member.getId(), member.getBranchId(), statusType.getId(), currentMember.getId()),
                new AttendanceSlotInformation(request.date(), request.slot(), null)
        ));
    }

    private void createSpecialLeave(Member currentMember, Member member, AttendanceSlotStatusUpdateRequest request) {
        String reason = request.reason();
        if (reason == null || reason.isBlank()) {
            reason = "기타";
        }
        specialLeaveRepository.save(new SpecialLeave(
                member.getId(),
                member.getBranchId(),
                request.date(),
                String.valueOf(request.slot()),
                reason.trim(),
                null,
                false,
                currentMember.getId()
        ));
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

    private Set<Long> findInitializedMemberIds(Long branchId, LocalDate date) {
        return attendanceDailyInitializationRepository.findByBranchIdAndAttendanceDate(branchId, date).stream()
                .map(AttendanceDailyInitialization::getMemberId)
                .collect(Collectors.toSet());
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
            Map<Long, List<String>> statusesByMemberId,
            Set<Long> initializedMemberIds
    ) {
        List<AttendanceBoardRowResponse> rows = new ArrayList<>();
        int lastSeatNumber = Math.max(MAX_SEAT_NUMBER, membersBySeat.keySet().stream().max(Comparator.naturalOrder()).orElse(0));
        for (int seatNumber = 1; seatNumber <= lastSeatNumber; seatNumber++) {
            Member member = membersBySeat.get(seatNumber);
            if (member == null) {
                rows.add(new AttendanceBoardRowResponse(null, seatNumber, "공석", null, null, null, emptySlots()));
                continue;
            }
            rows.add(new AttendanceBoardRowResponse(
                    member.getId(),
                    seatNumber,
                    member.getName(),
                    initializedMemberIds.contains(member.getId()) ? null : member.getJoinDate(),
                    member.getCreatedAt(),
                    getCertificationContent(member),
                    statusesByMemberId.get(member.getId())
            ));
        }
        members.stream()
                .filter(this::isUnassignedSeat)
                .forEach(member -> rows.add(new AttendanceBoardRowResponse(
                        member.getId(),
                        null,
                        member.getName(),
                        initializedMemberIds.contains(member.getId()) ? null : member.getJoinDate(),
                        member.getCreatedAt(),
                        getCertificationContent(member),
                        statusesByMemberId.get(member.getId())
                )));

        return rows;
    }

    private boolean isUnassignedSeat(Member member) {
        return member.getSeatNumber() == null || member.getSeatNumber() < 1;
    }

    private String getCertificationContent(Member member) {
        if (member.getCertificationId() == null) {
            return null;
        }

        return certificationRepository.findById(member.getCertificationId())
                .map(Certification::getContent)
                .orElse(null);
    }
}
