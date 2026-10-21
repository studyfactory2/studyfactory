package com.example.studyfactory.domain.leave.service;

import com.example.studyfactory.domain.attendance.repository.AttendanceUnconfirmedSlotRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceReviewedAbsenceRepository;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.leave.dto.EffectiveLeaveOccurrence;
import com.example.studyfactory.domain.leave.dto.FixedLeaveBatchCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveCancellationRequest;
import com.example.studyfactory.domain.leave.dto.LeaveCancellationResponse;
import com.example.studyfactory.domain.leave.dto.LeaveOccurrenceCancellationRequest;
import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.repository.LeaveOccurrenceCancellationRepository;
import com.example.studyfactory.domain.leave.dto.DailyLeaveStatusResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.FixedLeaveGenerationResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveManagementResponse;
import com.example.studyfactory.domain.leave.dto.FixedLeaveResponse;
import com.example.studyfactory.domain.leave.dto.LeaveCreateRequest;
import com.example.studyfactory.domain.leave.dto.LeaveResponse;
import com.example.studyfactory.domain.leave.dto.MemberLeavePlanResponse;
import com.example.studyfactory.domain.leave.dto.WeeklyLeaveAllowanceResponse;
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
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;

import java.time.Clock;
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
import java.util.Set;
import java.util.Objects;
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
    private final AttendanceUnconfirmedSlotRepository attendanceUnconfirmedSlotRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceReviewedAbsenceRepository attendanceReviewedAbsenceRepository;
    private final LeaveOccurrenceCancellationRepository cancellationRepository;
    private final EffectiveLeaveService effectiveLeaveService;
    private final WeeklyLeaveAllowanceCalculator weeklyLeaveAllowanceCalculator;
    private final Clock clock;

    @Transactional
    public LeaveResponse create(Long memberId, LeaveCreateRequest request) {
        validateLeaveDate(request.leaveDate());
        Member member = findMemberForUpdate(memberId);
        LeaveRequest leaveRequest = new LeaveRequest(
                member.getId(),
                member.getBranchId(),
                request.leaveDate(),
                request.leaveType()
        );

        attendanceUnconfirmedSlotRepository.deleteByMemberIdAndAttendanceDateAndSlotIn(
                memberId,
                request.leaveDate(),
                List.of(toSlotsForLeaveType(request.leaveType()).split(",")).stream().map(Integer::parseInt).toList()
        );
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        LocalDate weekStart = WeeklyLeaveAllowanceCalculator.weekStart(saved.getLeaveDate());
        Map<Long, WeeklyLeaveAllowanceResponse> allowances = weeklyLeaveAllowanceCalculator.calculate(
                effectiveLeaveService.findMemberRequestOccurrences(memberId, weekStart, weekStart.plusDays(6)));
        // The allowance is informational: excess requests are saved just like any other request.
        return LeaveResponse.from(saved, allowances.get(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> findMine(Long memberId) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId);
        if (leaves.isEmpty()) return List.of();
        List<EffectiveLeaveOccurrence> effectiveRequests = effectiveLeaveService.findMemberRequestOccurrences(memberId,
                        leaves.stream().map(LeaveRequest::getLeaveDate).min(LocalDate::compareTo).orElseThrow(),
                        leaves.stream().map(LeaveRequest::getLeaveDate).max(LocalDate::compareTo).orElseThrow());
        Map<Long, WeeklyLeaveAllowanceResponse> allowances = weeklyLeaveAllowanceCalculator.calculate(effectiveRequests);
        return leaves.stream().filter(leave -> allowances.containsKey(leave.getId()))
                .map(leave -> LeaveResponse.from(leave, allowances.get(leave.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberLeavePlanResponse> findMyLeavePlan(Long memberId, Integer year, Integer month) {
        YearMonth visibleMonth = resolveYearMonth(year, month);
        // This history includes every stored member request, so weeks spanning months remain complete.
        List<EffectiveLeaveOccurrence> history = effectiveLeaveService.findMemberHistory(
                memberId, visibleMonth.atDay(1), visibleMonth.atEndOfMonth());
        Map<Long, WeeklyLeaveAllowanceResponse> allowances = weeklyLeaveAllowanceCalculator.calculate(history);
        Map<String, MemberLeavePlanResponse> responses = new LinkedHashMap<>();
        history.stream()
                .forEach(leave -> {
                    String source = responseSource(leave);
                    Long id = leave.sourceType() == LeaveSourceType.SPECIAL_LEAVE ? null : leave.sourceId();
                    MemberLeavePlanResponse value = new MemberLeavePlanResponse(id, leave.date(), leave.leaveType(),
                            leave.reason(), source, toSlots(leave.slots().stream().toList()),
                            leave.sourceType() == LeaveSourceType.MEMBER_LEAVE ? allowances.get(leave.sourceId()) : null);
                    String key = source + "|" + leave.date() + "|" + (leave.sourceType() == LeaveSourceType.MEMBER_LEAVE ? leave.sourceId() : leave.reason());
                    responses.merge(key, value, (first, next) -> first.withSlots(mergeSlots(first.slots(), next.slots())));
                });
        return responses.values().stream()
                .sorted(Comparator.comparing(MemberLeavePlanResponse::leaveDate).reversed())
                .toList();
    }

    @Transactional
    public void delete(Long memberId, Long leaveId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId).orElseThrow(LeaveException::leaveNotFound);
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        validateOwner(member, leaveRequest);
        findMemberForUpdate(leaveRequest.getMemberId());
        leaveRequestRepository.delete(leaveRequest);
    }

    @Transactional(readOnly = true)
    public List<DailyLeaveStatusResponse> findDailyStatuses(
            Long currentMemberId,
            LocalDate date,
            String name,
            Long branchId,
            LeaveType leaveType
    ) {
        Member currentMember = findMember(currentMemberId);
        LocalDate targetDate = resolveDate(date);
        String searchName = toSearchName(name);
        Long targetBranchId = ManagerAccessPolicy.resolveOptionalAdminBranch(currentMember, branchId);
        List<EffectiveLeaveOccurrence> effective = effectiveLeaveService.findForBranch(targetBranchId, targetDate, targetDate);
        Map<Long, String> branchNames = new LinkedHashMap<>();
        leaveRequestRepository.findDailyStatuses(targetDate, searchName, targetBranchId, leaveType)
                .forEach(value -> branchNames.putIfAbsent(value.memberId(), value.branch()));
        Map<Long, Member> membersById = memberRepository.findAllById(
                        effective.stream().map(EffectiveLeaveOccurrence::memberId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(Member::getId, member -> member));
        Map<String, DailyLeaveStatusResponse> responses = new LinkedHashMap<>();
        for (EffectiveLeaveOccurrence leave : effective) {
            Member member = membersById.get(leave.memberId());
            LeaveType type = leave.leaveType() == null ? toManagerLeaveType(leave.reason()) : leave.leaveType();
            if (member == null || (searchName != null && !member.getName().contains(searchName))
                    || (leaveType != null && leaveType != type)) continue;
            responses.putIfAbsent(member.getId() + "|" + responseSource(leave) + "|" + leave.reason(),
                    new DailyLeaveStatusResponse(member.getId(), member.getBranchId(), member.getSeatNumber(), member.getName(),
                            branchNames.getOrDefault(member.getId(), ""), leave.date(), type, leave.createdAt(), leave.reason(), responseSource(leave),
                            isCreatedAfterEightInKorea(leave.createdAt(), leave.date())));
        }
        return List.copyOf(responses.values());
    }

    @Transactional(readOnly = true)
    public List<MonthlyLeaveCalendarResponse> findMonthlyCalendar(Long currentMemberId, Long memberId, Integer year, Integer month) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMember(memberId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        YearMonth yearMonth = resolveYearMonth(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        return effectiveLeaveService.findForMember(memberId, startDate, endDate).stream()
                .map(leave -> new MonthlyLeaveCalendarResponse(leave.date(),
                        leave.leaveType() == null ? leave.reason() : toLeaveTypeLabel(leave.leaveType()),
                        responseSource(leave), toSlots(leave.slots().stream().toList()), leave.sourceType(), leave.sourceId()))
                .toList();
    }

    @Transactional
    public List<SpecialLeaveResponse> createSpecial(Long currentMemberId, SpecialLeaveCreateRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(request.memberId());
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        validateSpecialLeaveRequest(request);
        String slots = toSlots(request.slots());
        String reason = request.reason().trim();
        String customReason = toNullableText(request.customReason());

        // A new explicit leave replaces the manual blank only where it applies.
        request.leaveDates().forEach(date -> attendanceUnconfirmedSlotRepository
                .deleteByMemberIdAndAttendanceDateAndSlotIn(targetMember.getId(), date, request.slots()));

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
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(request.memberId());
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        validateFixedLeaveRequest(request);
        validateRecurringDates(targetMember, List.of(request.leaveDate()), request.slots());
        return createFixedRule(targetMember, request.leaveDate(), request.slots(), request.reason());
    }

    @Transactional
    public List<FixedLeaveResponse> createFixedBatch(Long currentMemberId, FixedLeaveBatchCreateRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(request.memberId());
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        validateRecurringDates(targetMember, request.dates(), request.slots());
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 40) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
        Map<DayOfWeek, LocalDate> firstDates = new LinkedHashMap<>();
        request.dates().stream().sorted().forEach(date -> firstDates.putIfAbsent(date.getDayOfWeek(), date));
        // Validate the full batch before saving any rule, including for callers outside a transaction proxy.
        firstDates.values().forEach(date -> {
            if (hasFixedLeaveSlotConflict(targetMember.getId(), date, request.slots())) {
                throw LeaveException.fixedLeaveSlotAlreadyExists();
            }
        });
        return firstDates.values().stream()
                .map(date -> createFixedRule(targetMember, date, request.slots(), request.reason())).toList();
    }

    private FixedLeaveResponse createFixedRule(Member targetMember, LocalDate startDate, List<Integer> slots, String reason) {
        if (hasFixedLeaveSlotConflict(targetMember.getId(), startDate, slots)) {
            throw LeaveException.fixedLeaveSlotAlreadyExists();
        }
        FixedLeave fixedLeave = new FixedLeave(
                targetMember.getId(),
                targetMember.getBranchId(),
                startDate.getDayOfWeek(),
                toSlots(slots),
                reason.trim(),
                true,
                startDate
        );
        clearReplacedAttendanceOverrides(targetMember, fixedLeave, slots);
        return FixedLeaveResponse.from(fixedLeaveRepository.save(fixedLeave));
    }

    private void clearReplacedAttendanceOverrides(Member member, FixedLeave rule, List<Integer> slots) {
        List<Attendance> selectedAttendances = attendanceRepository.findFutureSelectedSlots(
                member.getId(), member.getBranchId(), rule.getStartDate(), slots).stream()
                .filter(attendance -> rule.appliesOn(attendance.getAttendanceDate())).toList();
        Set<AttendanceCell> presentCells = selectedAttendances.stream()
                .filter(attendance -> !Attendance.FIXED_LEAVE_CANCELLATION_MARKER.equals(attendance.getCustomStatusText()))
                .map(attendance -> new AttendanceCell(attendance.getAttendanceDate(), attendance.getSlot()))
                .collect(Collectors.toSet());
        // A new rule replaces previous explicit non-attendance overrides only where it now applies.
        // Never mutate real O rows, past dates, other weekdays, branches or unselected periods.
        selectedAttendances.stream()
                .filter(attendance -> Attendance.FIXED_LEAVE_CANCELLATION_MARKER.equals(attendance.getCustomStatusText()))
                .filter(attendance -> !presentCells.contains(new AttendanceCell(attendance.getAttendanceDate(), attendance.getSlot())))
                .forEach(attendanceRepository::delete);
        attendanceReviewedAbsenceRepository.findByMemberIdAndBranchIdAndAttendanceDateGreaterThanEqualAndSlotIn(
                        member.getId(), member.getBranchId(), rule.getStartDate(), slots).stream()
                .filter(value -> rule.appliesOn(value.getAttendanceDate()))
                .filter(value -> !presentCells.contains(new AttendanceCell(value.getAttendanceDate(), value.getSlot())))
                .forEach(attendanceReviewedAbsenceRepository::delete);
        attendanceUnconfirmedSlotRepository.findByMemberIdAndBranchIdAndAttendanceDateGreaterThanEqualAndSlotIn(
                        member.getId(), member.getBranchId(), rule.getStartDate(), slots).stream()
                .filter(value -> rule.appliesOn(value.getAttendanceDate()))
                .filter(value -> !presentCells.contains(new AttendanceCell(value.getAttendanceDate(), value.getSlot())))
                .forEach(attendanceUnconfirmedSlotRepository::delete);
    }

    private record AttendanceCell(LocalDate date, int slot) {}

    @Transactional
    public LeaveCancellationResponse cancelOccurrences(Long currentMemberId, LeaveCancellationRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member member = findMemberForUpdate(request.memberId());
        ManagerAccessPolicy.validateManagerTarget(currentMember, member);
        validateSelectedDates(member, request.dates(), request.slots());
        int cancelled = 0;
        int present = 0;
        for (LocalDate date : request.dates().stream().sorted().toList()) {
            Set<Integer> presentSlots = attendanceRepository.findByMemberIdAndAttendanceDateBetween(member.getId(), date, date).stream()
                    .filter(attendance -> !Attendance.FIXED_LEAVE_CANCELLATION_MARKER.equals(attendance.getCustomStatusText()))
                    .map(Attendance::getSlot).collect(Collectors.toSet());
            List<EffectiveLeaveOccurrence> occurrences = effectiveLeaveService.findForMember(member.getId(), date, date);
            for (Integer slot : request.slots()) {
                if (presentSlots.contains(slot)) {
                    present++;
                }
                boolean changed = false;
                for (EffectiveLeaveOccurrence leave : occurrences) {
                    if (!leave.slots().contains(slot)) continue;
                    cancellationRepository.save(new LeaveOccurrenceCancellation(member.getId(), member.getBranchId(),
                            leave.sourceType(), leave.sourceId(), date, slot, currentMember.getId()));
                    changed = true;
                }
                if (changed) cancelled++;
            }
        }
        return new LeaveCancellationResponse(member.getId(), member.getBranchId(), request.dates().size() * request.slots().size(), cancelled, present);
    }

    @Transactional
    public LeaveCancellationResponse cancelOccurrence(Long currentMemberId, LeaveOccurrenceCancellationRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member member = findMemberForUpdate(request.memberId());
        ManagerAccessPolicy.validateManagerTarget(currentMember, member);
        ManagerAccessPolicy.validateBranch(currentMember, request.branchId());
        if (!Objects.equals(member.getBranchId(), request.branchId())) {
            throw MemberException.forbidden();
        }
        validateSelectedDates(member, List.of(request.leaveDate()), List.of(request.slot()));

        Optional<EffectiveLeaveOccurrence> occurrence = effectiveLeaveService.findForMember(
                        member.getId(), request.branchId(), request.leaveDate(), request.leaveDate()).stream()
                .filter(leave -> leave.sourceType() == request.sourceType()
                        && Objects.equals(leave.sourceId(), request.sourceId())
                        && leave.slots().contains(request.slot()))
                .findFirst();
        // Stale rows and retries are harmless: never widen a missing source to the whole cell.
        occurrence.ifPresent(leave -> cancellationRepository.save(new LeaveOccurrenceCancellation(
                member.getId(), request.branchId(), leave.sourceType(), leave.sourceId(),
                request.leaveDate(), request.slot(), currentMember.getId())));
        boolean present = attendanceRepository.findByMemberIdAndBranchIdAndAttendanceDateBetween(
                        member.getId(), request.branchId(), request.leaveDate(), request.leaveDate()).stream()
                .anyMatch(attendance -> attendance.getSlot() == request.slot()
                        && !Attendance.FIXED_LEAVE_CANCELLATION_MARKER.equals(attendance.getCustomStatusText()));
        return new LeaveCancellationResponse(member.getId(), request.branchId(), 1,
                occurrence.isPresent() ? 1 : 0, present ? 1 : 0);
    }

    @Transactional(readOnly = true)
    public List<FixedLeaveManagementResponse> findFixedLeaves(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        Long targetBranchId = ManagerAccessPolicy.resolveOptionalAdminBranch(currentMember, branchId);
        String searchName = toSearchName(name);
        List<FixedLeave> fixedLeaves = fixedLeaveRepository.findByActiveTrueOrderByCreatedAtAsc();
        Map<Long, Member> membersById = memberRepository.findAllById(fixedLeaves.stream().map(FixedLeave::getMemberId).toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        return fixedLeaves.stream()
                .filter(fixedLeave -> fixedLeave.continuesOnOrAfter(LocalDate.now(clock)))
                .filter(fixedLeave -> targetBranchId == null || fixedLeave.getBranchId().equals(targetBranchId))
                .filter(fixedLeave -> matchesFixedLeaveMember(fixedLeave, membersById, searchName))
                .map(fixedLeave -> FixedLeaveManagementResponse.from(fixedLeave, membersById.get(fixedLeave.getMemberId())))
                .toList();
    }

    @Transactional
    public void deleteFixed(Long currentMemberId, Long fixedLeaveId) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Long ownerId = fixedLeaveRepository.findOwnerIdById(fixedLeaveId).orElseThrow(LeaveException::leaveNotFound);
        Member owner = findMemberForUpdate(ownerId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, owner);
        FixedLeave fixedLeave = fixedLeaveRepository.findById(fixedLeaveId).orElseThrow(LeaveException::leaveNotFound);
        ManagerAccessPolicy.validateBranch(currentMember, fixedLeave.getBranchId());
        fixedLeave.stopFrom(LocalDate.now(clock));
        specialLeaveRepository.deleteByFixedLeaveIdAndLeaveDateGreaterThanEqual(
                fixedLeave.getId(),
                LocalDate.now(clock)
        );
    }

    @Transactional
    public FixedLeaveGenerationResponse generateFixedLeaves(Long currentMemberId) {
        Member currentMember = findMember(currentMemberId);
        validateAdmin(currentMember);
        return generateFixedLeaves(currentMember);
    }

    @Transactional
    public Optional<FixedLeaveGenerationResponse> generateFixedLeavesBySystem() {
        return memberRepository.findFirstByRoleOrderByIdAsc(MemberRole.ADMIN)
                .map(this::generateFixedLeaves);
    }

    private FixedLeaveGenerationResponse generateFixedLeaves(Member createdByMember) {
        LocalDate startDate = LocalDate.now(clock);
        LocalDate endDate = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(13);
        int createdCount = 0;
        // Discover only IDs before locking so a waiting generator cannot retain stale rule entities.
        for (Long memberId : fixedLeaveRepository.findRuleMemberIds()) {
            findMemberForUpdate(memberId);
            List<FixedLeave> rules = fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(memberId);
            List<EffectiveLeaveOccurrence> occurrences = effectiveLeaveService.findForMember(memberId, startDate, endDate);
            // Preserve all historical rows and every unrelated/null-origin legacy record.
            rules.stream().filter(FixedLeave::isActive)
                    .forEach(rule -> specialLeaveRepository.deleteByFixedLeaveIdAndLeaveDateGreaterThanEqual(rule.getId(), startDate));
            for (EffectiveLeaveOccurrence leave : occurrences) {
                if (leave.sourceType() != LeaveSourceType.FIXED_LEAVE) continue;
                specialLeaveRepository.save(new SpecialLeave(leave.memberId(), leave.branchId(), leave.date(),
                        toSlots(leave.slots().stream().toList()), leave.reason(), null, true, createdByMember.getId(), leave.sourceId()));
                createdCount++;
            }
        }

        return new FixedLeaveGenerationResponse(startDate, endDate, createdCount);
    }

    @Transactional(readOnly = true)
    public List<SpecialLeaveResponse> findSpecialByMember(Long currentMemberId, Long memberId) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMember(memberId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);

        List<SpecialLeave> leaves = specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId);
        if (leaves.isEmpty()) return List.of();
        List<EffectiveLeaveOccurrence> effective = effectiveLeaveService.findMemberSpecialHistory(memberId);
        List<SpecialLeaveResponse> responses = new ArrayList<>();
        for (SpecialLeave leave : leaves) {
            effective.stream().filter(value -> value.date().equals(leave.getLeaveDate()))
                    .filter(value -> (value.sourceType() == LeaveSourceType.SPECIAL_LEAVE && Objects.equals(value.sourceId(), leave.getId()))
                            || (value.sourceType() == LeaveSourceType.FIXED_LEAVE && Objects.equals(value.sourceId(), leave.getFixedLeaveId())))
                    .findFirst().ifPresent(value -> responses.add(new SpecialLeaveResponse(leave.getId(), leave.getMemberId(),
                            leave.getBranchId(), leave.getLeaveDate(), toSlots(value.slots().stream().toList()), leave.getReason(),
                            leave.getCustomReason(), leave.isRecurring(), leave.getCreatedByMemberId(), leave.getCreatedAt(), leave.getUpdatedAt())));
        }
        return responses;
    }

    @Transactional
    public void deleteSpecialSlot(Long currentMemberId, Long specialLeaveId, Integer slot) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        validateSlot(slot);
        SpecialLeave specialLeave = specialLeaveRepository.findById(specialLeaveId).orElseThrow(LeaveException::leaveNotFound);
        ManagerAccessPolicy.validateBranch(currentMember, specialLeave.getBranchId());
        Member member = findMemberForUpdate(specialLeave.getMemberId());
        effectiveLeaveService.findForMember(member.getId(), specialLeave.getLeaveDate(), specialLeave.getLeaveDate()).stream()
                .filter(value -> (value.sourceType() == LeaveSourceType.SPECIAL_LEAVE && Objects.equals(value.sourceId(), specialLeaveId))
                        || (value.sourceType() == LeaveSourceType.FIXED_LEAVE && Objects.equals(value.sourceId(), specialLeave.getFixedLeaveId())))
                .filter(value -> value.slots().contains(slot))
                .forEach(value -> cancellationRepository.save(new LeaveOccurrenceCancellation(member.getId(), member.getBranchId(),
                        value.sourceType(), value.sourceId(), value.date(), slot, currentMemberId)));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private LocalDate resolveDate(LocalDate date) {
        if (date == null) {
            return LocalDate.now(clock);
        }

        return date;
    }

    private YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null || month == null) {
            return YearMonth.now(clock);
        }

        return YearMonth.of(year, month);
    }

    private void validateLeaveDate(LocalDate leaveDate) {
        if (leaveDate.isBefore(LocalDate.now(clock))) {
            throw LeaveException.pastDateNotAllowed();
        }
    }

    private void validateOwner(Member member, LeaveRequest leaveRequest) {
        if (member.getRole() == MemberRole.ADMIN) {
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

    private void validateAdmin(Member member) {
        if (member.getRole() != MemberRole.ADMIN) {
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
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 40) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
        if (request.slots().stream().anyMatch(slot -> slot < 1 || slot > 7)) {
            throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private boolean hasFixedLeaveSlotConflict(Long memberId, LocalDate startDate, List<Integer> requestedSlots) {
        return fixedLeaveRepository.findByMemberIdAndActiveTrueOrderByCreatedAtAsc(memberId)
                .stream()
                .filter(fixedLeave -> fixedLeave.getDayOfWeek() == startDate.getDayOfWeek())
                .filter(fixedLeave -> fixedLeave.continuesOnOrAfter(startDate))
                .flatMap(fixedLeave -> EffectiveLeaveService.parseSlots(fixedLeave.getSlots()).stream())
                .anyMatch(requestedSlots::contains);
    }

    private void validateRecurringDates(Member member, List<LocalDate> dates, List<Integer> slots) {
        validateSelectedDates(member, dates, slots);
        // A selected-day correction must not make new recurring rules retroactive.
        dates.forEach(this::validateLeaveDate);
    }

    private void validateSelectedDates(Member member, List<LocalDate> dates, List<Integer> slots) {
        if (dates == null || dates.isEmpty() || dates.size() > 31 || dates.stream().anyMatch(Objects::isNull)
                || dates.stream().distinct().count() != dates.size()
                || slots == null || slots.isEmpty() || slots.size() > 7 || slots.stream().anyMatch(slot -> slot == null || slot < 1 || slot > 7)
                || slots.stream().distinct().count() != slots.size()) throw LeaveException.invalidSpecialLeaveRequest();
        for (LocalDate date : dates) {
            if (member.getJoinDate() != null && date.isBefore(member.getJoinDate())) throw LeaveException.invalidSpecialLeaveRequest();
        }
    }

    private String responseSource(EffectiveLeaveOccurrence leave) {
        return leave.sourceType() == LeaveSourceType.MEMBER_LEAVE ? "LEAVE" : leave.sourceType().name();
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

    private String mergeSlots(String first, String second) {
        return java.util.stream.Stream.concat(
                        java.util.Arrays.stream((first == null ? "" : first).split(",")),
                        java.util.Arrays.stream((second == null ? "" : second).split(","))
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Integer::valueOf)
                .filter(slot -> slot >= 1 && slot <= 7)
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

    private String toSlotsForLeaveType(LeaveType leaveType) {
        if (leaveType == LeaveType.FULL) {
            return "1,2,3,4,5,6,7";
        }
        if (leaveType == LeaveType.MORNING) {
            return "1,2,3,4";
        }

        return "4,5,6,7";
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

    private boolean isCreatedAfterEightInKorea(LocalDateTime createdAt, LocalDate leaveDate) {
        if (createdAt == null) {
            return false;
        }

        LocalDateTime createdAtInKorea = createdAt
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(clock.getZone())
                .toLocalDateTime();

        java.time.LocalTime requestedTime = createdAtInKorea.toLocalTime();
        return createdAtInKorea.toLocalDate().equals(leaveDate)
                && !requestedTime.isBefore(java.time.LocalTime.of(8, 0))
                && requestedTime.isBefore(java.time.LocalTime.of(9, 0));
    }
}
