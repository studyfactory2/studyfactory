package com.example.studyfactory.domain.weeklyPlan.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;
import com.example.studyfactory.domain.weeklyPlan.dto.BreakPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanGroupResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanItemResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanUpdateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanWeekResponse;
import com.example.studyfactory.domain.weeklyPlan.entity.BreakPlanCreateReceipt;
import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanItem;
import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanWeek;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.exception.SharedPlanException;
import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import com.example.studyfactory.domain.weeklyPlan.model.PlanItemSource;
import com.example.studyfactory.domain.weeklyPlan.repository.BreakPlanCreateReceiptRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.SharedPlanItemRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.SharedPlanWeekRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanItemRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SharedPlanService {

    private final MemberRepository memberRepository;
    private final WeeklyPlanItemRepository weeklyPlanItemRepository;
    private final WeeklyPlanGoalRepository weeklyPlanGoalRepository;
    private final SharedPlanWeekRepository sharedPlanWeekRepository;
    private final SharedPlanItemRepository sharedPlanItemRepository;
    private final BreakPlanCreateReceiptRepository breakPlanCreateReceiptRepository;

    // A response's revision and items must come from the same database snapshot.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SharedPlanWeekResponse findMine(Long memberId, LocalDate weekStartDate) {
        Member member = findMember(memberId);
        validateWeek(weekStartDate);
        return snapshot(member, weekStartDate);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SharedPlanWeekResponse findForManager(Long currentMemberId, Long memberId, LocalDate weekStartDate) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMember(memberId);
        if (currentMember.getRole() != MemberRole.ADMIN) {
            ManagerAccessPolicy.validateMemberTarget(currentMember, targetMember);
        }
        validateWeek(weekStartDate);
        return snapshot(targetMember, weekStartDate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SharedPlanWeekResponse createMine(Long memberId, SharedPlanCreateRequest request) {
        if (request == null) {
            throw SharedPlanException.invalidRequest("저장할 계획을 입력해 주세요.");
        }
        validateWeek(request.weekStartDate());
        validateRevision(request.expectedRevision());
        if (request.dayIndex() == null || request.dayIndex() < 0 || request.dayIndex() > 6
                || request.group() == null || request.clientRequestId() == null) {
            throw SharedPlanException.invalidRequest("날짜, 교시 그룹, 저장 요청 번호를 확인해 주세요.");
        }
        String content = validateContent(request.content());
        Member member = findMemberForUpdate(memberId);
        // Create tokens share one member-scoped namespace across group and break plans.
        if (breakPlanCreateReceiptRepository.findByMemberIdAndClientRequestId(memberId, request.clientRequestId()).isPresent()) {
            throw SharedPlanException.requestIdReused();
        }
        String fingerprint = createFingerprint(request, content);
        SharedPlanItem previous = sharedPlanItemRepository
                .findByMemberIdAndClientRequestId(memberId, request.clientRequestId()).orElse(null);
        if (previous != null) {
            if (!previous.getCreateFingerprint().equals(fingerprint)) {
                throw SharedPlanException.requestIdReused();
            }
            // A lost response may be retried after edits or deletion; never recreate the item.
            return snapshot(member, request.weekStartDate());
        }

        SharedPlanWeek week = checkedWeek(memberId, request.weekStartDate(), request.expectedRevision());
        int lastOrder = sharedPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByDayIndexAscSortOrderAscIdAsc(memberId, request.weekStartDate())
                .stream()
                .filter(item -> item.getDayIndex() == request.dayIndex() && item.getGroup() == request.group())
                .mapToInt(SharedPlanItem::getSortOrder).max().orElse(-1);
        if (lastOrder == Integer.MAX_VALUE) {
            throw SharedPlanException.invalidRequest("이 교시 그룹에 계획을 더 추가할 수 없습니다.");
        }
        sharedPlanItemRepository.save(new SharedPlanItem(memberId, member.getBranchId(), request.weekStartDate(),
                request.dayIndex(), request.group(), content, lastOrder + 1, request.clientRequestId(), fingerprint));
        advance(week);
        return snapshot(member, request.weekStartDate());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SharedPlanWeekResponse saveGoalMine(Long memberId, SharedPlanGoalRequest request) {
        if (request == null || request.goal() == null) {
            throw SharedPlanException.invalidRequest("주간 목표를 입력해 주세요. 목표를 비우려면 빈 문자열을 보내 주세요.");
        }
        validateWeek(request.weekStartDate());
        validateRevision(request.expectedRevision());
        Member member = findMemberForUpdate(memberId);
        SharedPlanWeek week = checkedWeek(memberId, request.weekStartDate(), request.expectedRevision());
        WeeklyPlanGoal goal = weeklyPlanGoalRepository.findByMemberIdAndWeekStartDate(memberId, request.weekStartDate())
                .orElseGet(() -> new WeeklyPlanGoal(memberId, member.getBranchId(), request.weekStartDate(), ""));
        goal.updateGoal(request.goal().trim());
        weeklyPlanGoalRepository.save(goal);
        advance(week);
        return snapshot(member, request.weekStartDate());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SharedPlanWeekResponse createBreakMine(Long memberId, BreakPlanCreateRequest request) {
        if (request == null) {
            throw SharedPlanException.invalidRequest("저장할 점심 또는 저녁 계획을 입력해 주세요.");
        }
        validateWeek(request.weekStartDate());
        validateRevision(request.expectedRevision());
        if (request.dayIndex() == null || request.dayIndex() < 0 || request.dayIndex() > 6
                || request.periodIndex() == null || (request.periodIndex() != 100 && request.periodIndex() != 101)
                || request.clientRequestId() == null) {
            throw SharedPlanException.invalidRequest("날짜, 점심 또는 저녁 구분, 저장 요청 번호를 확인해 주세요.");
        }
        String content = validateContent(request.content());
        Member member = findMemberForUpdate(memberId);
        if (sharedPlanItemRepository.findByMemberIdAndClientRequestId(memberId, request.clientRequestId()).isPresent()) {
            throw SharedPlanException.requestIdReused();
        }
        String fingerprint = fingerprint("BREAK\n" + request.weekStartDate() + "\n" + request.dayIndex()
                + "\n" + request.periodIndex() + "\n" + content);
        BreakPlanCreateReceipt previous = breakPlanCreateReceiptRepository
                .findByMemberIdAndClientRequestId(memberId, request.clientRequestId()).orElse(null);
        if (previous != null) {
            if (!previous.getCreateFingerprint().equals(fingerprint)) {
                throw SharedPlanException.requestIdReused();
            }
            // Receipt survives item deletion: retries return current state without restoring it.
            return snapshot(member, request.weekStartDate());
        }

        SharedPlanWeek week = checkedWeek(memberId, request.weekStartDate(), request.expectedRevision());
        int lastOrder = Math.max(-1, weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(memberId, request.weekStartDate())
                .stream()
                .filter(item -> item.getDayIndex() == request.dayIndex() && item.getPeriodIndex() == request.periodIndex())
                .mapToInt(WeeklyPlanItem::getSortOrder).max().orElse(-1));
        if (lastOrder == Integer.MAX_VALUE) {
            throw SharedPlanException.invalidRequest("이 시간대에 계획을 더 추가할 수 없습니다.");
        }
        // These are genuinely period-specific plans, not shared-group tasks anchored to a period.
        WeeklyPlanItem item = weeklyPlanItemRepository.save(new WeeklyPlanItem(memberId, member.getBranchId(),
                request.weekStartDate(), request.periodIndex(), request.dayIndex(), content, false, lastOrder + 1));
        breakPlanCreateReceiptRepository.save(new BreakPlanCreateReceipt(memberId, request.weekStartDate(),
                request.clientRequestId(), fingerprint, item.getId()));
        advance(week);
        return snapshot(member, request.weekStartDate());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SharedPlanWeekResponse updateMine(Long memberId, PlanItemSource source, Long itemId, SharedPlanUpdateRequest request) {
        if (request == null || (request.content() == null && request.done() == null)) {
            throw SharedPlanException.invalidRequest("변경할 내용이나 완료 상태를 입력해 주세요.");
        }
        validateWeek(request.weekStartDate());
        validateRevision(request.expectedRevision());
        validateItemReference(source, itemId);
        String content = request.content() == null ? null : validateContent(request.content());
        Member member = findMemberForUpdate(memberId);
        SharedPlanWeek week = checkedWeek(memberId, request.weekStartDate(), request.expectedRevision());
        if (source == PlanItemSource.LEGACY) {
            findLegacyItem(memberId, request.weekStartDate(), itemId).update(content, request.done());
        } else {
            findSharedItem(memberId, request.weekStartDate(), itemId).update(content, request.done());
        }
        advance(week);
        return snapshot(member, request.weekStartDate());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SharedPlanWeekResponse deleteMine(Long memberId, PlanItemSource source, Long itemId,
                                             LocalDate weekStartDate, Long expectedRevision) {
        validateWeek(weekStartDate);
        validateRevision(expectedRevision);
        validateItemReference(source, itemId);
        Member member = findMemberForUpdate(memberId);
        SharedPlanWeek week = checkedWeek(memberId, weekStartDate, expectedRevision);
        if (source == PlanItemSource.LEGACY) {
            weeklyPlanItemRepository.delete(findLegacyItem(memberId, weekStartDate, itemId));
        } else {
            // Keep the create token even after deletion, so a late retry cannot resurrect a task.
            findSharedItem(memberId, weekStartDate, itemId).markDeleted();
        }
        advance(week);
        return snapshot(member, weekStartDate);
    }

    private SharedPlanWeek checkedWeek(Long memberId, LocalDate weekStartDate, Long expectedRevision) {
        SharedPlanWeek week = sharedPlanWeekRepository.findByMemberIdAndWeekStartDate(memberId, weekStartDate)
                .orElseGet(() -> new SharedPlanWeek(memberId, weekStartDate));
        if (week.getRevision() != expectedRevision) {
            throw SharedPlanException.staleRevision();
        }
        return week;
    }

    private void advance(SharedPlanWeek week) {
        week.advance(true);
        sharedPlanWeekRepository.save(week);
    }

    private WeeklyPlanItem findLegacyItem(Long memberId, LocalDate weekStartDate, Long itemId) {
        return weeklyPlanItemRepository.findByIdAndMemberIdAndWeekStartDate(itemId, memberId, weekStartDate)
                .orElseThrow(SharedPlanException::itemNotFound);
    }

    private SharedPlanItem findSharedItem(Long memberId, LocalDate weekStartDate, Long itemId) {
        return sharedPlanItemRepository.findByIdAndMemberIdAndWeekStartDate(itemId, memberId, weekStartDate)
                .filter(item -> !item.isDeleted()).orElseThrow(SharedPlanException::itemNotFound);
    }

    private SharedPlanWeekResponse snapshot(Member member, LocalDate weekStartDate) {
        SharedPlanWeek week = sharedPlanWeekRepository.findByMemberIdAndWeekStartDate(member.getId(), weekStartDate)
                .orElse(null);
        List<SharedPlanItemResponse> items = new ArrayList<>();
        weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(member.getId(), weekStartDate)
                .forEach(item -> items.add(new SharedPlanItemResponse(item.getId(), PlanItemSource.LEGACY,
                        item.getDayIndex(), item.getPeriodIndex(), legacyGroup(item), item.getContent(),
                        item.isDone(), item.getSortOrder())));
        sharedPlanItemRepository.findByMemberIdAndWeekStartDateOrderByDayIndexAscSortOrderAscIdAsc(member.getId(), weekStartDate)
                .stream().filter(item -> !item.isDeleted())
                .forEach(item -> items.add(new SharedPlanItemResponse(item.getId(), PlanItemSource.GROUP,
                        item.getDayIndex(), null, item.getGroup(), item.getContent(), item.isDone(), item.getSortOrder())));

        // Existing per-period rows retain their original order; new shared rows follow them.
        items.sort(Comparator.comparingInt(SharedPlanItemResponse::dayIndex)
                .thenComparing(SharedPlanItemResponse::periodIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(SharedPlanItemResponse::sortOrder)
                .thenComparing(SharedPlanItemResponse::id));
        List<SharedPlanGroupResponse> groups = Arrays.stream(PlanGroup.values())
                .map(group -> new SharedPlanGroupResponse(group,
                        items.stream().filter(item -> item.group() == group).toList())).toList();
        String goal = weeklyPlanGoalRepository.findByMemberIdAndWeekStartDate(member.getId(), weekStartDate)
                .map(WeeklyPlanGoal::getGoal).orElse("");
        return new SharedPlanWeekResponse(member.getId(), member.getBranchId(), weekStartDate,
                week == null ? 0 : week.getRevision(), week != null && week.isLegacySaveProtected(), goal,
                groups, items.stream().filter(item -> item.group() == null).toList());
    }

    private PlanGroup legacyGroup(WeeklyPlanItem item) {
        return item.getDayIndex() < 0 || item.getDayIndex() > 6 ? null : PlanGroup.fromPeriodIndex(item.getPeriodIndex());
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private Member findMemberForUpdate(Long memberId) {
        // Shared and legacy writers serialize on the same existing row, including first saves.
        return memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateWeek(LocalDate weekStartDate) {
        if (weekStartDate == null || weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw SharedPlanException.invalidRequest("해당 주의 월요일 날짜를 지정해 주세요.");
        }
    }

    private void validateRevision(Long expectedRevision) {
        if (expectedRevision == null || expectedRevision < 0) {
            throw SharedPlanException.invalidRequest("조회한 계획의 버전이 필요합니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private void validateItemReference(PlanItemSource source, Long itemId) {
        if (source == null || itemId == null || itemId <= 0) {
            throw SharedPlanException.invalidRequest("계획 항목을 확인해 주세요.");
        }
    }

    private String validateContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isBlank()) {
            throw SharedPlanException.invalidRequest("계획 내용을 입력해 주세요.");
        }
        return trimmed;
    }

    private String createFingerprint(SharedPlanCreateRequest request, String content) {
        String value = request.weekStartDate() + "\n" + request.dayIndex() + "\n" + request.group().name() + "\n" + content;
        return fingerprint(value);
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
