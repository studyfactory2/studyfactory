package com.example.studyfactory.domain.weeklyPlan.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanItemRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanItemResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanSaveRequest;
import com.example.studyfactory.domain.weeklyPlan.entity.MonthlyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.repository.MonthlyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanItemRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeeklyPlanService {

    private final MemberRepository memberRepository;
    private final WeeklyPlanGoalRepository weeklyPlanGoalRepository;
    private final WeeklyPlanItemRepository weeklyPlanItemRepository;
    private final MonthlyPlanGoalRepository monthlyPlanGoalRepository;

    @Transactional(readOnly = true)
    public WeeklyPlanResponse findMine(Long memberId, LocalDate weekStartDate) {
        Member member = findMember(memberId);
        LocalDate resolvedWeekStartDate = resolveWeekStartDate(weekStartDate);
        WeeklyPlanGoal goal = weeklyPlanGoalRepository
                .findByMemberIdAndWeekStartDate(member.getId(), resolvedWeekStartDate)
                .orElse(null);
        List<WeeklyPlanItemResponse> items = weeklyPlanItemRepository
                .findByMemberIdAndWeekStartDateOrderByPeriodIndexAscDayIndexAscSortOrderAscIdAsc(member.getId(), resolvedWeekStartDate)
                .stream()
                .map(WeeklyPlanItemResponse::from)
                .toList();

        return new WeeklyPlanResponse(
                goal == null ? null : goal.getId(),
                member.getId(),
                member.getBranchId(),
                resolvedWeekStartDate,
                goal == null ? "" : goal.getGoal(),
                items
        );
    }

    @Transactional(readOnly = true)
    public WeeklyPlanResponse findForManager(Long currentMemberId, Long memberId, LocalDate weekStartDate) {
        Member currentMember = findMember(currentMemberId);
        if (!currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }

        return findMine(memberId, weekStartDate);
    }

    @Transactional
    public WeeklyPlanResponse saveMine(Long memberId, WeeklyPlanSaveRequest request) {
        Member member = findMember(memberId);
        LocalDate weekStartDate = resolveWeekStartDate(request.weekStartDate());
        WeeklyPlanGoal goal = weeklyPlanGoalRepository
                .findByMemberIdAndWeekStartDate(member.getId(), weekStartDate)
                .orElseGet(() -> new WeeklyPlanGoal(member.getId(), member.getBranchId(), weekStartDate, ""));

        goal.updateGoal(toText(request.goal()));
        WeeklyPlanGoal savedGoal = weeklyPlanGoalRepository.save(goal);
        weeklyPlanItemRepository.deleteByMemberIdAndWeekStartDate(member.getId(), weekStartDate);
        List<WeeklyPlanItem> items = toItems(member, weekStartDate, request.items());
        weeklyPlanItemRepository.saveAll(items);

        return new WeeklyPlanResponse(
                savedGoal.getId(),
                member.getId(),
                member.getBranchId(),
                weekStartDate,
                savedGoal.getGoal(),
                items.stream()
                        .sorted(Comparator
                                .comparingInt(WeeklyPlanItem::getPeriodIndex)
                                .thenComparingInt(WeeklyPlanItem::getDayIndex)
                                .thenComparingInt(WeeklyPlanItem::getSortOrder))
                        .map(WeeklyPlanItemResponse::from)
                        .toList()
        );
    }

    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        weeklyPlanItemRepository.deleteByMemberId(memberId);
        weeklyPlanGoalRepository.deleteByMemberId(memberId);
        monthlyPlanGoalRepository.deleteByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public MonthlyPlanGoalResponse findMonthlyGoal(Long memberId, String month) {
        Member member = findMember(memberId);
        YearMonth yearMonth = resolveMonth(month);

        return monthlyPlanGoalRepository.findByMemberIdAndMonth(member.getId(), yearMonth.toString())
                .map(MonthlyPlanGoalResponse::from)
                .orElseGet(() -> MonthlyPlanGoalResponse.empty(member.getId(), member.getBranchId(), yearMonth.toString()));
    }

    @Transactional
    public MonthlyPlanGoalResponse saveMonthlyGoal(Long memberId, MonthlyPlanGoalRequest request) {
        Member member = findMember(memberId);
        YearMonth yearMonth = resolveMonth(request.month());
        MonthlyPlanGoal monthlyPlanGoal = monthlyPlanGoalRepository
                .findByMemberIdAndMonth(member.getId(), yearMonth.toString())
                .orElseGet(() -> new MonthlyPlanGoal(member.getId(), member.getBranchId(), yearMonth, ""));

        monthlyPlanGoal.updateGoal(toText(request.goal()));

        return MonthlyPlanGoalResponse.from(monthlyPlanGoalRepository.save(monthlyPlanGoal));
    }

    private List<WeeklyPlanItem> toItems(Member member, LocalDate weekStartDate, List<WeeklyPlanItemRequest> requests) {
        if (requests == null) {
            return List.of();
        }

        return requests.stream()
                .map(request -> new WeeklyPlanItem(
                        member.getId(),
                        member.getBranchId(),
                        weekStartDate,
                        request.periodIndex(),
                        request.dayIndex(),
                        toText(request.content()),
                        request.done(),
                        request.sortOrder()
                ))
                .filter(item -> !item.getContent().isBlank())
                .toList();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private LocalDate resolveWeekStartDate(LocalDate weekStartDate) {
        if (weekStartDate == null) {
            return LocalDate.now();
        }

        return weekStartDate;
    }

    private YearMonth resolveMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }

        return YearMonth.parse(month);
    }

    private String toText(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }
}
