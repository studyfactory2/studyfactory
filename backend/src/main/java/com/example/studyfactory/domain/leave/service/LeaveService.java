package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.FixedLeaveGenerationResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveManagementResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MemberLeavePlanResponse;
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
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public List<MemberLeavePlanResponse> findMyLeavePlan(Long memberId, Integer year, Integer month) {
        List<MemberLeavePlanResponse> responses = new ArrayList<>();
        Map<String, MemberLeavePlanResponse> managerLeavesByDateAndReason = new LinkedHashMap<>();
        Map<String, MemberLeavePlanResponse> fixedLeavesByDateAndReason = new LinkedHashMap<>();
        YearMonth visibleMonth = resolveYearMonth(year, month);

        leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId)
                .forEach(leaveRequest -> responses.add(MemberLeavePlanResponse.fromLeaveRequest(
                        leaveRequest,
                        toLeaveHistoryLabel(leaveRequest.getLeaveType())
                )));
        specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId)
                .forEach(specialLeave -> {
                    String label = toSpecialLeaveLabel(specialLeave);
                    managerLeavesByDateAndReason.putIfAbsent(
                            specialLeave.getLeaveDate() + "|" + label,
                            MemberLeavePlanResponse.fromSpecialLeave(specialLeave, label)
                    );
                });
        responses.addAll(managerLeavesByDateAndReason.values());

        LocalDate date = visibleMonth.atDay(1);
        LocalDate endDate = visibleMonth.atEndOfMonth();
        List<FixedLeave> fixedLeaves = fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(memberId);
        while (!date.isAfter(endDate)) {
            LocalDate leaveDate = date;
            fixedLeaves.stream()
                    .filter(fixedLeave -> fixedLeave.getDayOfWeek() == leaveDate.getDayOfWeek())
                    .forEach(fixedLeave -> fixedLeavesByDateAndReason.putIfAbsent(
                            leaveDate + "|" + fixedLeave.getReason(),
                            MemberLeavePlanResponse.fromFixedLeave(fixedLeave, leaveDate, fixedLeave.getReason())
                    ));
            date = date.plusDays(1);
        }
        responses.addAll(fixedLeavesByDateAndReason.values());

        return responses.stream()
                .sorted(Comparator.comparing(MemberLeavePlanResponse::leaveDate).reversed())
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
        LocalDate targetDate = resolveDate(date);
        String searchName = toSearchName(name);
        List<DailyLeaveStatusResponse> responses = new ArrayList<>(
                leaveRequestRepository.findDailyStatuses(targetDate, searchName, branchId, leaveType)
        );
        Map<String, DailyLeaveStatusResponse> managerLeaveStatuses = new LinkedHashMap<>();
        List<SpecialLeave> specialLeaves = branchId == null
                ? specialLeaveRepository.findByLeaveDateOrderByCreatedAtAsc(targetDate)
                : specialLeaveRepository.findByBranchIdAndLeaveDateOrderByCreatedAtAsc(branchId, targetDate);
        Map<Long, Member> membersById = memberRepository.findAllById(
                        specialLeaves.stream().map(SpecialLeave::getMemberId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        for (SpecialLeave specialLeave : specialLeaves) {
            LeaveType managerLeaveType = toManagerLeaveType(specialLeave.getReason());
            Member member = membersById.get(specialLeave.getMemberId());
            if (member == null) {
                continue;
            }
            if (searchName != null && !member.getName().contains(searchName)) {
                continue;
            }
            if (leaveType != null && leaveType != managerLeaveType) {
                continue;
            }

            managerLeaveStatuses.putIfAbsent(
                    String.valueOf(member.getId()),
                    new DailyLeaveStatusResponse(
                            member.getId(),
                            member.getBranchId(),
                            member.getSeatNumber(),
                            member.getName(),
                            "",
                            specialLeave.getLeaveDate(),
                            managerLeaveType,
                            specialLeave.getCreatedAt(),
                            toSpecialLeaveLabel(specialLeave),
                            "SPECIAL_LEAVE",
                            isCreatedAfterEightInKorea(specialLeave)
                    )
            );
        }
        responses.addAll(managerLeaveStatuses.values());

        return responses;
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

    @Transactional
    public FixedLeaveResponse createFixed(Long currentMemberId, FixedLeaveCreateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(request.memberId());
        validateFixedLeaveRequest(request);
        if (hasFixedLeaveSlotConflict(targetMember.getId(), request.leaveDate().getDayOfWeek(), request.slots())) {
            throw LeaveException.fixedLeaveSlotAlreadyExists();
        }
        FixedLeave fixedLeave = new FixedLeave(
                targetMember.getId(),
                targetMember.getBranchId(),
                request.leaveDate().getDayOfWeek(),
                toSlots(request.slots()),
                request.reason().trim(),
                true
        );

        return FixedLeaveResponse.from(fixedLeaveRepository.save(fixedLeave));
    }

    @Transactional(readOnly = true)
    public List<FixedLeaveManagementResponse> findFixedLeaves(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        String searchName = toSearchName(name);
        List<FixedLeave> fixedLeaves = fixedLeaveRepository.findByActiveTrueOrderByCreatedAtAsc();
        Map<Long, Member> membersById = memberRepository.findAllById(fixedLeaves.stream().map(FixedLeave::getMemberId).toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        return fixedLeaves.stream()
                .filter(fixedLeave -> branchId == null || fixedLeave.getBranchId().equals(branchId))
                .filter(fixedLeave -> matchesFixedLeaveMember(fixedLeave, membersById, searchName))
                .map(fixedLeave -> FixedLeaveManagementResponse.from(fixedLeave, membersById.get(fixedLeave.getMemberId())))
                .toList();
    }

    @Transactional
    public void deleteFixed(Long currentMemberId, Long fixedLeaveId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        FixedLeave fixedLeave = fixedLeaveRepository.findById(fixedLeaveId).orElseThrow(LeaveException::leaveNotFound);
        fixedLeaveRepository.delete(fixedLeave);
    }

    @Transactional
    public FixedLeaveGenerationResponse generateFixedLeaves(Long currentMemberId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        return generateFixedLeaves(currentMember);
    }

    @Transactional
    public Optional<FixedLeaveGenerationResponse> generateFixedLeavesBySystem() {
        return memberRepository.findFirstByRoleOrderByIdAsc(MemberRole.ADMIN)
                .map(this::generateFixedLeaves);
    }

    private FixedLeaveGenerationResponse generateFixedLeaves(Member createdByMember) {
        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(13);
        specialLeaveRepository.deleteAll(specialLeaveRepository.findByRecurringTrueAndLeaveDateBetween(startDate, endDate));

        int createdCount = 0;
        List<FixedLeave> fixedLeaves = fixedLeaveRepository.findByActiveTrueOrderByCreatedAtAsc();
        for (FixedLeave fixedLeave : fixedLeaves) {
            LocalDate leaveDate = startDate;
            while (!leaveDate.isAfter(endDate)) {
                if (leaveDate.getDayOfWeek() == fixedLeave.getDayOfWeek()) {
                    specialLeaveRepository.save(new SpecialLeave(
                            fixedLeave.getMemberId(),
                            fixedLeave.getBranchId(),
                            leaveDate,
                            fixedLeave.getSlots(),
                            fixedLeave.getReason(),
                            null,
                            true,
                            createdByMember.getId()
                    ));
                    createdCount++;
                }
                leaveDate = leaveDate.plusDays(1);
            }
        }

        return new FixedLeaveGenerationResponse(startDate, endDate, createdCount);
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

    private void validateFixedLeaveRequest(FixedLeaveCreateRequest request) {
        if (request.reason().isBlank()) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
        if (request.slots().stream().anyMatch(slot -> slot < 1 || slot > 7)) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private boolean hasFixedLeaveSlotConflict(Long memberId, DayOfWeek dayOfWeek, List<Integer> requestedSlots) {
        return fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(memberId)
                .stream()
                .filter(fixedLeave -> fixedLeave.getDayOfWeek() == dayOfWeek)
                .flatMap(fixedLeave -> List.of(fixedLeave.getSlots().split(",")).stream())
                .map(String::trim)
                .map(Integer::parseInt)
                .anyMatch(requestedSlots::contains);
    }

    private void validateSlot(Integer slot) {
        if (slot == null || slot < 1 || slot > 7) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private boolean matchesFixedLeaveMember(FixedLeave fixedLeave, Map<Long, Member> membersById, String searchName) {
        Member member = membersById.get(fixedLeave.getMemberId());
        if (member == null) {
            return false;
        }
        if (searchName == null) {
            return true;
        }

        return member.getName().contains(searchName);
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

    private String toLeaveTypeLabel(LeaveType leaveType) {
        if (leaveType == LeaveType.FULL) {
            return "월차";
        }
        if (leaveType == LeaveType.MORNING) {
            return "오전";
        }

        return "오후";
    }

    private String toLeaveHistoryLabel(LeaveType leaveType) {
        if (leaveType == LeaveType.FULL) {
            return "월차";
        }
        if (leaveType == LeaveType.MORNING) {
            return "오전반차";
        }

        return "오후반차";
    }

    private LeaveType toManagerLeaveType(String reason) {
        if ("오전반차".equals(reason)) {
            return LeaveType.MORNING;
        }
        if ("오후반차".equals(reason)) {
            return LeaveType.AFTERNOON;
        }

        return LeaveType.FULL;
    }

    private String toSpecialLeaveLabel(SpecialLeave specialLeave) {
        if ("기타".equals(specialLeave.getReason()) && specialLeave.getCustomReason() != null) {
            return specialLeave.getCustomReason();
        }

        return specialLeave.getReason();
    }

    private boolean isCreatedAfterEightInKorea(SpecialLeave specialLeave) {
        LocalDateTime createdAt = specialLeave.getCreatedAt();
        if (createdAt == null) {
            return false;
        }

        LocalDateTime createdAtInKorea = createdAt
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toLocalDateTime();

        java.time.LocalTime requestedTime = createdAtInKorea.toLocalTime();
        return createdAtInKorea.toLocalDate().equals(specialLeave.getLeaveDate())
                && !requestedTime.isBefore(java.time.LocalTime.of(8, 0))
                && requestedTime.isBefore(java.time.LocalTime.of(9, 0));
    }
}
