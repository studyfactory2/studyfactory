package com.example.studyfactory.domain.leave.service;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Date- and source-aware leave projection shared by attendance, calendars and study calculations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EffectiveLeaveService {
    private final LeaveRequestRepository leaveRequestRepository;
    private final SpecialLeaveRepository specialLeaveRepository;
    private final FixedLeaveRepository fixedLeaveRepository;
    private final LeaveOccurrenceCancellationRepository cancellationRepository;

    /** Request history needs source cancellation checks, never recurring projection across its date span. */
    public List<EffectiveLeaveOccurrence> findMemberRequestOccurrences(Long memberId, LocalDate start, LocalDate end) {
        return resolve(start, end,
                leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, start, end),
                List.of(), List.of(), cancellationRepository.findByMemberIdAndLeaveDateBetween(memberId, start, end));
    }

    /** Keep all stored history, but project rules only for the visible interval and their stored occurrence dates. */
    public List<EffectiveLeaveOccurrence> findMemberHistory(Long memberId, LocalDate visibleStart, LocalDate visibleEnd) {
        List<LeaveRequest> requests = leaveRequestRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId);
        List<SpecialLeave> specials = specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId);
        return resolveStoredHistory(memberId, requests, specials, datesBetween(visibleStart, visibleEnd).toList());
    }

    /** Manager history represents stored records only, not every occurrence between their dates. */
    public List<EffectiveLeaveOccurrence> findMemberSpecialHistory(Long memberId) {
        return resolveStoredHistory(memberId, List.of(),
                specialLeaveRepository.findByMemberIdOrderByLeaveDateDescCreatedAtDesc(memberId), List.of());
    }

    private List<EffectiveLeaveOccurrence> resolveStoredHistory(Long memberId, List<LeaveRequest> requests,
            List<SpecialLeave> specials, List<LocalDate> visibleDates) {
        Set<LocalDate> storedDates = new HashSet<>(visibleDates);
        requests.forEach(leave -> storedDates.add(leave.getLeaveDate()));
        specials.forEach(leave -> storedDates.add(leave.getLeaveDate()));
        if (storedDates.isEmpty()) return List.of();
        List<FixedLeave> rules = fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(memberId);
        Map<Long, Set<LocalDate>> materializedDates = new HashMap<>();
        specials.stream().filter(leave -> leave.getFixedLeaveId() != null).forEach(leave ->
                materializedDates.computeIfAbsent(leave.getFixedLeaveId(), ignored -> new HashSet<>()).add(leave.getLeaveDate()));
        List<LeaveOccurrenceCancellation> cancellations = cancellationRepository.findByMemberIdAndLeaveDateBetween(memberId,
                storedDates.stream().min(LocalDate::compareTo).orElseThrow(),
                storedDates.stream().max(LocalDate::compareTo).orElseThrow());
        return resolve(requests, specials, rules, cancellations, rule -> Stream.concat(
                visibleDates.stream(),
                materializedDates.getOrDefault(rule.getId(), Set.of()).stream()).distinct());
    }

    public List<EffectiveLeaveOccurrence> findForMember(Long memberId, LocalDate start, LocalDate end) {
        return resolve(start, end,
                leaveRequestRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, start, end),
                specialLeaveRepository.findByMemberIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, start, end),
                fixedLeaveRepository.findByMemberIdOrderByCreatedAtAsc(memberId),
                cancellationRepository.findByMemberIdAndLeaveDateBetween(memberId, start, end));
    }

    public List<EffectiveLeaveOccurrence> findForMember(Long memberId, Long branchId, LocalDate start, LocalDate end) {
        return resolve(start, end,
                leaveRequestRepository.findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, branchId, start, end),
                specialLeaveRepository.findByMemberIdAndBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(memberId, branchId, start, end),
                fixedLeaveRepository.findByMemberIdAndBranchIdOrderByCreatedAtAsc(memberId, branchId),
                cancellationRepository.findByMemberIdAndBranchIdAndLeaveDateBetween(memberId, branchId, start, end));
    }

    public List<EffectiveLeaveOccurrence> findForBranch(Long branchId, LocalDate start, LocalDate end) {
        return branchId == null
                ? resolve(start, end,
                    leaveRequestRepository.findByLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(start, end),
                    specialLeaveRepository.findByLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(start, end),
                    fixedLeaveRepository.findAllByOrderByCreatedAtAsc(),
                    cancellationRepository.findByLeaveDateBetween(start, end))
                : resolve(start, end,
                    leaveRequestRepository.findByBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(branchId, start, end),
                    specialLeaveRepository.findByBranchIdAndLeaveDateBetweenOrderByLeaveDateAscCreatedAtAsc(branchId, start, end),
                    fixedLeaveRepository.findByBranchIdOrderByCreatedAtAsc(branchId),
                    cancellationRepository.findByBranchIdAndLeaveDateBetween(branchId, start, end));
    }

    private List<EffectiveLeaveOccurrence> resolve(LocalDate start, LocalDate end,
            List<LeaveRequest> requests, List<SpecialLeave> specials, List<FixedLeave> rules,
            List<LeaveOccurrenceCancellation> cancellations) {
        return resolve(requests, specials, rules, cancellations, ignored -> datesBetween(start, end));
    }

    private List<EffectiveLeaveOccurrence> resolve(List<LeaveRequest> requests, List<SpecialLeave> specials,
            List<FixedLeave> rules, List<LeaveOccurrenceCancellation> cancellations,
            Function<FixedLeave, Stream<LocalDate>> datesForRule) {
        Map<SourceDate, Set<Integer>> cancelled = new HashMap<>();
        for (LeaveOccurrenceCancellation value : cancellations) {
            cancelled.computeIfAbsent(new SourceDate(value.getSourceType(), value.getSourceId(), value.getLeaveDate()),
                    ignored -> new HashSet<>()).add(value.getSlot());
        }
        List<EffectiveLeaveOccurrence> result = new ArrayList<>();
        for (LeaveRequest leave : requests) {
            add(result, cancelled, new EffectiveLeaveOccurrence(leave.getMemberId(), leave.getBranchId(),
                    LeaveSourceType.MEMBER_LEAVE, leave.getId(), leave.getLeaveDate(), slotsForType(leave.getLeaveType()),
                    labelForType(leave.getLeaveType()), leave.getLeaveType(), leave.getCreatedAt()));
        }
        Set<Long> ruleIds = rules.stream().filter(FixedLeave::isActive).map(FixedLeave::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (FixedLeave rule : rules) {
            if (!rule.isActive()) continue;
            datesForRule.apply(rule).filter(rule::appliesOn).forEach(date ->
                    add(result, cancelled, new EffectiveLeaveOccurrence(rule.getMemberId(), rule.getBranchId(),
                            LeaveSourceType.FIXED_LEAVE, rule.getId(), date, parseSlots(rule.getSlots()),
                            rule.getReason(), null, rule.getCreatedAt())));
        }
        for (SpecialLeave leave : specials) {
            // Linked generated rows are a cache of the rule, not a second independent leave.
            // Unknown/null legacy origins remain independent records; never guess a matching rule.
            if (leave.getFixedLeaveId() != null && ruleIds.contains(leave.getFixedLeaveId())) {
                continue;
            }
            String reason = "기타".equals(leave.getReason()) && leave.getCustomReason() != null && !leave.getCustomReason().isBlank()
                    ? leave.getCustomReason() : leave.getReason();
            add(result, cancelled, new EffectiveLeaveOccurrence(leave.getMemberId(), leave.getBranchId(),
                    LeaveSourceType.SPECIAL_LEAVE, leave.getId(), leave.getLeaveDate(), parseSlots(leave.getSlots()),
                    reason, null, leave.getCreatedAt()));
        }
        return result;
    }

    private Stream<LocalDate> datesBetween(LocalDate start, LocalDate end) {
        return start.isAfter(end) ? Stream.empty() : Stream.concat(start.datesUntil(end), Stream.of(end));
    }

    private void add(List<EffectiveLeaveOccurrence> result, Map<SourceDate, Set<Integer>> cancellations,
                     EffectiveLeaveOccurrence value) {
        Set<Integer> slots = new TreeSet<>(value.slots());
        slots.removeAll(cancellations.getOrDefault(new SourceDate(value.sourceType(), value.sourceId(), value.date()), Set.of()));
        if (!slots.isEmpty()) {
            result.add(new EffectiveLeaveOccurrence(value.memberId(), value.branchId(), value.sourceType(), value.sourceId(),
                    value.date(), slots, value.reason(), value.leaveType(), value.createdAt()));
        }
    }

    public static Set<Integer> parseSlots(String value) {
        Set<Integer> result = new TreeSet<>();
        if (value == null) return result;
        for (String part : value.split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (slot >= 1 && slot <= 7) result.add(slot);
            } catch (NumberFormatException ignored) {
                // Malformed legacy values do not invalidate other valid periods.
            }
        }
        return result;
    }

    public static Set<Integer> slotsForType(LeaveType type) {
        return type == LeaveType.FULL ? Set.of(1, 2, 3, 4, 5, 6, 7)
                : type == LeaveType.MORNING ? Set.of(1, 2, 3, 4) : Set.of(4, 5, 6, 7);
    }

    public static String labelForType(LeaveType type) {
        return type == LeaveType.FULL ? "월차" : type == LeaveType.MORNING ? "오전반차" : "오후반차";
    }

    private record SourceDate(LeaveSourceType type, Long id, LocalDate date) {}
}
