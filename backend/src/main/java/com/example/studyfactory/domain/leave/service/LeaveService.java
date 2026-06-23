package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MonthlyLeaveCalendarResponse;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.SpecialLeaveResponse;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.leave.exception.LeaveException;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.leave.repository.SpecialLeaveRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public LeaveResponse create(Long memberId, LeaveCreateRequest request) {
        validateLeaveDate(request.leaveDate());
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        LeaveRequest leaveRequest = new LeaveRequest(
                member.getId(),
                member.getBranchId(),
                request.leaveDate(),
                request.leaveType()
        );

        return LeaveResponse.from(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> findMine(Long memberId) {
        return leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId)
                .stream()
                .map(LeaveResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long memberId, Long leaveId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId).orElseThrow(LeaveException::leaveNotFound);
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        validateOwner(member, leaveRequest);
        leaveRequestRepository.delete(leaveRequest);
    }

    @Transactional(readOnly = true)
    public List<DailyLeaveStatusResponse> findDailyStatuses(LocalDate date, String name, Long branchId, LeaveType leaveType) {
        return leaveRequestRepository.findDailyStatuses(resolveDate(date), toSearchName(name), branchId, leaveType);
    }

    @Transactional(readOnly = true)
    public List<MonthlyLeaveCalendarResponse> findMonthlyCalendar(Long currentMemberId, Long memberId, Integer year, Integer month) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        findMember(memberId);
        YearMonth yearMonth = resolveYearMonth(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        List<MonthlyLeaveCalendarResponse> responses = new ArrayList<>();

        leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, startDate, endDate)
                .forEach(leaveRequest -> responses.add(new MonthlyLeaveCalendarResponse(
                        leaveRequest.getLeaveDate(),
                        toLeaveTypeLabel(leaveRequest.getLeaveType()),
                        "LEAVE"
                )));

        fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(memberId)
                .forEach(fixedLeave -> addFixedLeaves(responses, fixedLeave, startDate, endDate));

        specialLeaveRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, startDate, endDate)
                .forEach(specialLeave -> responses.add(new MonthlyLeaveCalendarResponse(
                        specialLeave.getLeaveDate(),
                        toSpecialLeaveLabel(specialLeave),
                        "SPECIAL_LEAVE"
                )));

        return responses;
    }

    @Transactional
    public List<SpecialLeaveResponse> createSpecial(Long currentMemberId, SpecialLeaveCreateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(request.memberId());
        validateSpecialLeaveRequest(request);
        String slots = toSlots(request.slots());
        String reason = request.reason().trim();
        String customReason = toNullableText(request.customReason());

        return request.leaveDates()
                .stream()
                .sorted()
                .map(leaveDate -> new SpecialLeave(
                        targetMember.getId(),
                        targetMember.getBranchId(),
                        leaveDate,
                        slots,
                        reason,
                        customReason,
                        request.recurring(),
                        currentMember.getId()
                ))
                .map(specialLeaveRepository::save)
                .map(SpecialLeaveResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialLeaveResponse> findSpecialByMember(Long currentMemberId, Long memberId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        findMember(memberId);

        return specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId)
                .stream()
                .map(SpecialLeaveResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSpecialSlot(Long currentMemberId, Long specialLeaveId, Integer slot) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        validateSlot(slot);
        SpecialLeave specialLeave = specialLeaveRepository.findById(specialLeaveId).orElseThrow(LeaveException::leaveNotFound);
        boolean empty = specialLeave.removeSlot(slot);

        if (!empty && !specialLeave.getSlots().contains(String.valueOf(slot))) {
            return;
        }
        if (empty) {
            specialLeaveRepository.delete(specialLeave);
        }
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private LocalDate resolveDate(LocalDate date) {
        if (date == null) {
            return LocalDate.now();
        }

        return date;
    }

    private YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            return YearMonth.now();
        }

        return YearMonth.of(year, month);
    }

    private void validateLeaveDate(LocalDate leaveDate) {
        if (leaveDate.isBefore(LocalDate.now())) {
            throw LeaveException.pastDateNotAllowed();
        }
    }

    private void validateOwner(Member member, LeaveRequest leaveRequest) {
        if (member.hasAllPermissions()) {
            return;
        }
        if (!leaveRequest.getMemberId().equals(member.getId())) {
            throw LeaveException.notOwner();
        }
    }

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private void validateSpecialLeaveRequest(SpecialLeaveCreateRequest request) {
        if (request.reason().isBlank()) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
        if (request.slots().stream().anyMatch(slot -> slot < 1 || slot > 7)) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private void validateSlot(Integer slot) {
        if (slot == null || slot < 1 || slot > 7) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private String toSlots(List<Integer> slots) {
        return slots.stream()
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private String toNullableText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        return text.trim();
    }

    private void addFixedLeaves(
            List<MonthlyLeaveCalendarResponse> responses,
            FixedLeave fixedLeave,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            if (date.getDayOfWeek() == fixedLeave.getDayOfWeek()) {
                responses.add(new MonthlyLeaveCalendarResponse(date, fixedLeave.getReason(), "FIXED_LEAVE"));
            }
            date = date.plusDays(1);
        }
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
}
