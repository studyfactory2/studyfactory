package com.example.studyfactory.domain.weeklyPlan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.studyfactory.domain.auth.jwt.JwtTokenProvider;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.service.StudyTimeReportService;
import com.example.studyfactory.domain.weeklyPlan.dto.BreakPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanItemResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanUpdateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanWeekResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanSaveRequest;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import com.example.studyfactory.domain.weeklyPlan.model.PlanItemSource;
import com.example.studyfactory.domain.weeklyPlan.repository.BreakPlanCreateReceiptRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.MonthlyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.SharedPlanItemRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.SharedPlanWeekRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Isolated, real H2 transactions; no production database or SQL migration is used. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shared-plan-compatibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000",
        "study-break.reconciliation.enabled=false",
        "study-presence.auto-close.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("주간 목표 및 점심·저녁 계획 안전 저장 통합 테스트")
class SharedPlanCompatibilityIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 26);
    private static final LocalDate OTHER_WEEK = WEEK.plusWeeks(1);
    private static final Instant NOW = Instant.parse("2026-10-26T13:30:00Z");

    @Autowired private SharedPlanService sharedPlans;
    @Autowired private WeeklyPlanService legacyPlans;
    @Autowired private SharedPlanItemRepository sharedItems;
    @Autowired private SharedPlanWeekRepository sharedWeeks;
    @Autowired private BreakPlanCreateReceiptRepository receipts;
    @Autowired private WeeklyPlanItemRepository legacyItems;
    @Autowired private WeeklyPlanGoalRepository weeklyGoals;
    @Autowired private MonthlyPlanGoalRepository monthlyGoals;
    @Autowired private MemberRepository members;
    @Autowired private BranchRepository branches;
    @Autowired private StudyPresenceSessionRepository presences;
    @Autowired private StudyBreakSessionRepository breaks;
    @Autowired private StudyTimeReportService studyReports;
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;

    private Branch branch;
    private Member member;

    @BeforeEach
    void resetPrivateDatabase() {
        breaks.deleteAll();
        presences.deleteAll();
        receipts.deleteAll();
        sharedItems.deleteAll();
        sharedWeeks.deleteAll();
        legacyItems.deleteAll();
        weeklyGoals.deleteAll();
        monthlyGoals.deleteAll();
        members.deleteAll();
        branches.deleteAll();
        branch = branches.saveAndFlush(new Branch("망미점", "부산"));
        member = newMember("김회원", 12);
    }

    @Test
    void goalOnlySavePreservesExistingIdOwnershipAllPlanRowsAndMonthlyGoal() {
        WeeklyPlanGoal original = weeklyGoals.saveAndFlush(new WeeklyPlanGoal(
                member.getId(), branch.getId(), WEEK, "기존 주간 목표"));
        var originalCreatedAt = weeklyGoals.findById(original.getId()).orElseThrow().getCreatedAt();
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), OTHER_WEEK, "다음 주 목표"));
        legacy(0, 0, "기존 1교시", true, 9);
        legacy(100, 0, "기존 점심", false, 5);
        legacy(101, 2, "기존 저녁", true, 3);
        legacy(42, 7, "그룹 밖 기록도 보존", false, 0);
        legacyItems.saveAndFlush(new WeeklyPlanItem(member.getId(), branch.getId(), OTHER_WEEK,
                6, 3, "다음 주 계획", false, 1));
        legacyPlans.saveMonthlyGoal(member.getId(), new MonthlyPlanGoalRequest("2026-10", "월간 목표"));
        SharedPlanWeekResponse grouped = createGroup(0L, "공유 계획");
        List<String> before = legacySnapshots();
        var beforeGroups = grouped.groups();

        SharedPlanWeekResponse response = sharedPlans.saveGoalMine(member.getId(),
                new SharedPlanGoalRequest(WEEK, 1L, "새 주간 목표"));

        assertThat(response.revision()).isEqualTo(2L);
        assertThat(response.legacySaveProtected()).isTrue();
        assertThat(response.goal()).isEqualTo("새 주간 목표");
        assertThat(response.groups()).isEqualTo(beforeGroups);
        assertThat(legacySnapshots()).isEqualTo(before);
        WeeklyPlanGoal persisted = weeklyGoals.findByMemberIdAndWeekStartDate(member.getId(), WEEK).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(original.getId());
        assertThat(persisted.getMemberId()).isEqualTo(original.getMemberId());
        assertThat(persisted.getBranchId()).isEqualTo(original.getBranchId());
        assertThat(persisted.getWeekStartDate()).isEqualTo(WEEK);
        assertThat(persisted.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(weeklyGoals.findByMemberIdAndWeekStartDate(member.getId(), OTHER_WEEK).orElseThrow().getGoal())
                .isEqualTo("다음 주 목표");
        assertThat(legacyPlans.findMonthlyGoal(member.getId(), "2026-10").goal()).isEqualTo("월간 목표");
    }

    @Test
    void goalCanBeCreatedThenClearedWithoutRemovingItsStableRow() {
        SharedPlanWeekResponse created = sharedPlans.saveGoalMine(member.getId(),
                new SharedPlanGoalRequest(WEEK, 0L, "처음 작성한 목표"));
        Long id = weeklyGoals.findByMemberIdAndWeekStartDate(member.getId(), WEEK).orElseThrow().getId();

        SharedPlanWeekResponse cleared = sharedPlans.saveGoalMine(member.getId(),
                new SharedPlanGoalRequest(WEEK, created.revision(), ""));

        assertThat(cleared.goal()).isEmpty();
        assertThat(cleared.revision()).isEqualTo(2L);
        assertThat(cleared.legacySaveProtected()).isTrue();
        assertThat(weeklyGoals.findAll()).singleElement().satisfies(goal -> {
            assertThat(goal.getId()).isEqualTo(id);
            assertThat(goal.getGoal()).isEmpty();
        });
        assertThat(legacyItems.count()).isZero();
        assertThat(sharedItems.count()).isZero();
    }

    @Test
    void goalSaveProtectsAnOtherwiseEmptyWeekFromWholeWeekReplacement() {
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, "새 목표"));

        assertStatus(409, () -> legacyPlans.saveMine(member.getId(),
                new WeeklyPlanSaveRequest(WEEK, "오래된 전체 저장", List.of())));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).goal()).isEqualTo("새 목표");
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
    }

    @Test
    void staleGoalCannotOverwriteGoalOrIncrementRevision() {
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, "먼저 저장"));

        assertStatus(409, () -> sharedPlans.saveGoalMine(member.getId(),
                new SharedPlanGoalRequest(WEEK, 0L, "오래된 수정")));
        assertStatus(409, () -> createGroup(0L, "오래된 공유 추가"));
        assertStatus(409, () -> createBreak(0L, 0, 100, "오래된 점심 추가", UUID.randomUUID()));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).goal()).isEqualTo("먼저 저장");
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(sharedItems.count()).isZero();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void goalValidationDoesNotCreateOrActivateAWeek() {
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), null));
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, null)));
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(null, 0L, "목표")));
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK.plusDays(1), 0L, "목표")));
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, null, "목표")));
        assertStatus(400, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, -1L, "목표")));

        assertThat(sharedWeeks.count()).isZero();
        assertThat(weeklyGoals.count()).isZero();
    }

    @Test
    void lunchAndDinnerAppendOnlyToTheirOwnDayAndPeriodWithoutRewritingSavedRows() {
        legacy(100, 2, "원래 점심", true, 8);
        legacy(100, 3, "다른 날 점심", false, 99);
        legacy(101, 2, "원래 저녁", false, 4);
        legacy(0, 2, "기존 교시 계획", true, 100);
        legacy(42, 2, "알 수 없는 기존 교시", false, 2);
        List<String> originals = legacySnapshots();
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), WEEK, "보존할 목표"));

        SharedPlanWeekResponse lunch = createBreak(0L, 2, 100, "  점심 단어 복습  ", UUID.randomUUID());
        SharedPlanWeekResponse dinner = createBreak(lunch.revision(), 2, 101, "저녁 오답 정리", UUID.randomUUID());

        assertThat(dinner.revision()).isEqualTo(2L);
        assertThat(dinner.legacySaveProtected()).isTrue();
        assertThat(dinner.goal()).isEqualTo("보존할 목표");
        assertThat(legacySnapshots()).containsAll(originals);
        assertThat(legacyItems.count()).isEqualTo(7L);
        assertThat(sharedItems.count()).isZero();
        assertThat(receipts.count()).isEqualTo(2L);
        assertThat(findContent(dinner, "점심 단어 복습")).satisfies(item -> {
            assertThat(item.source()).isEqualTo(PlanItemSource.LEGACY);
            assertThat(item.periodIndex()).isEqualTo(100);
            assertThat(item.dayIndex()).isEqualTo(2);
            assertThat(item.sortOrder()).isEqualTo(9);
            assertThat(item.done()).isFalse();
            assertThat(item.group()).isNull();
        });
        assertThat(findContent(dinner, "저녁 오답 정리")).satisfies(item -> {
            assertThat(item.periodIndex()).isEqualTo(101);
            assertThat(item.sortOrder()).isEqualTo(5);
        });
        assertStatus(409, () -> legacyPlans.saveMine(member.getId(), new WeeklyPlanSaveRequest(WEEK, "덮어쓰기", List.of())));
    }

    @Test
    void newBreakPlanSupportsTargetedEditCompletionAndDeletionWhileKeepingItsReceipt() {
        WeeklyPlanItem untouched = legacy(101, 0, "저녁 보존", true, 4);
        SharedPlanWeekResponse created = createBreak(0L, 0, 100, "점심 시작", UUID.randomUUID());
        Long id = findContent(created, "점심 시작").id();
        WeeklyPlanItem before = legacyItems.findById(id).orElseThrow();

        SharedPlanWeekResponse edited = sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "점심 수정", true));

        WeeklyPlanItem after = legacyItems.findById(id).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPeriodIndex()).isEqualTo(before.getPeriodIndex());
        assertThat(after.getDayIndex()).isEqualTo(before.getDayIndex());
        assertThat(after.getSortOrder()).isEqualTo(before.getSortOrder());
        assertThat(after.isDone()).isTrue();
        assertThat(edited.revision()).isEqualTo(2L);

        SharedPlanWeekResponse deleted = sharedPlans.deleteMine(member.getId(), PlanItemSource.LEGACY, id, WEEK, 2L);

        assertThat(deleted.revision()).isEqualTo(3L);
        assertThat(deleted.legacySaveProtected()).isTrue();
        assertThat(legacyItems.findById(id)).isEmpty();
        assertThat(legacyItems.findById(untouched.getId()).orElseThrow().getContent()).isEqualTo("저녁 보존");
        assertThat(receipts.count()).isEqualTo(1L);
    }

    @Test
    void matchingBreakRetryAfterEditAndHardDeletionNeverDuplicatesOrResurrectsThePlan() {
        UUID token = UUID.randomUUID();
        BreakPlanCreateRequest request = breakRequest(WEEK, 0L, 0, 100, "원본 점심", token);
        Long id = findContent(sharedPlans.createBreakMine(member.getId(), request), "원본 점심").id();
        sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "나중에 수정", true));

        SharedPlanWeekResponse retried = sharedPlans.createBreakMine(member.getId(), request);
        assertThat(retried.revision()).isEqualTo(2L);
        assertThat(findContent(retried, "나중에 수정").id()).isEqualTo(id);
        assertThat(legacyItems.count()).isEqualTo(1L);
        sharedPlans.deleteMine(member.getId(), PlanItemSource.LEGACY, id, WEEK, 2L);

        SharedPlanWeekResponse deletedRetry = sharedPlans.createBreakMine(member.getId(), request);

        assertThat(deletedRetry.revision()).isEqualTo(3L);
        assertThat(deletedRetry.ungroupedItems()).isEmpty();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.findByMemberIdAndClientRequestId(member.getId(), token)).isPresent();
        assertThat(receipts.count()).isEqualTo(1L);
    }

    @Test
    void breakRequestTokenCannotBeReusedWithDifferentTextDateDayOrPeriod() {
        UUID token = UUID.randomUUID();
        createBreak(0L, 1, 100, "원본", token);

        assertStatus(409, () -> createBreak(1L, 1, 100, "변경된 내용", token));
        assertStatus(409, () -> createBreak(1L, 2, 100, "원본", token));
        assertStatus(409, () -> createBreak(1L, 1, 101, "원본", token));
        assertStatus(409, () -> sharedPlans.createBreakMine(member.getId(),
                breakRequest(OTHER_WEEK, 0L, 1, 100, "원본", token)));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(sharedPlans.findMine(member.getId(), OTHER_WEEK).revision()).isZero();
        assertThat(legacyItems.count()).isEqualTo(1L);
        assertThat(receipts.count()).isEqualTo(1L);
    }

    @Test
    void requestTokenCannotCrossFromBreakToGroupEvenAfterHardDelete() {
        UUID token = UUID.randomUUID();
        Long id = findContent(createBreak(0L, 0, 100, "점심", token), "점심").id();
        sharedPlans.deleteMine(member.getId(), PlanItemSource.LEGACY, id, WEEK, 1L);

        assertStatus(409, () -> sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, 2L, 0, PlanGroup.PERIODS_1_2, "공유 계획", token)));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(2L);
        assertThat(sharedItems.count()).isZero();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isEqualTo(1L);
    }

    @Test
    void requestTokenCannotCrossFromGroupToBreakEvenAfterTombstone() {
        UUID token = UUID.randomUUID();
        SharedPlanWeekResponse created = sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, 0L, 0, PlanGroup.PERIODS_1_2, "공유 계획", token));
        Long id = findContent(created, "공유 계획").id();
        sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP, id, WEEK, 1L);

        assertStatus(409, () -> createBreak(2L, 0, 100, "점심", token));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(2L);
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isZero();
        assertThat(sharedItems.findAll()).singleElement().satisfies(item -> assertThat(item.isDeleted()).isTrue());
    }

    @Test
    void requestTokensAreScopedToTheMemberNotGlobal() {
        Member other = newMember("다른 회원", 13);
        UUID token = UUID.randomUUID();
        createBreak(0L, 0, 100, "내 점심", token);

        SharedPlanWeekResponse otherResult = sharedPlans.createBreakMine(other.getId(),
                breakRequest(WEEK, 0L, 0, 101, "다른 회원 저녁", token));

        assertThat(otherResult.memberId()).isEqualTo(other.getId());
        assertThat(otherResult.ungroupedItems()).singleElement().satisfies(item -> assertThat(item.content()).isEqualTo("다른 회원 저녁"));
        assertThat(sharedPlans.findMine(member.getId(), WEEK).ungroupedItems()).singleElement()
                .satisfies(item -> assertThat(item.content()).isEqualTo("내 점심"));
        assertThat(receipts.count()).isEqualTo(2L);
    }

    @Test
    void invalidBreakCreatesNeverWriteStateItemOrReceipt() {
        assertStatus(400, () -> sharedPlans.createBreakMine(member.getId(), null));
        for (Integer invalidPeriod : new Integer[]{null, -1, 0, 6, 99, 102}) {
            assertStatus(400, () -> sharedPlans.createBreakMine(member.getId(),
                    breakRequest(WEEK, 0L, 0, invalidPeriod, "내용", UUID.randomUUID())));
        }
        for (Integer invalidDay : new Integer[]{null, -1, 7}) {
            assertStatus(400, () -> sharedPlans.createBreakMine(member.getId(),
                    breakRequest(WEEK, 0L, invalidDay, 100, "내용", UUID.randomUUID())));
        }
        assertStatus(400, () -> createBreak(0L, 0, 100, null, UUID.randomUUID()));
        assertStatus(400, () -> createBreak(0L, 0, 100, " \n ", UUID.randomUUID()));
        assertStatus(400, () -> createBreak(null, 0, 100, "내용", UUID.randomUUID()));
        assertStatus(400, () -> createBreak(-1L, 0, 100, "내용", UUID.randomUUID()));
        assertStatus(400, () -> createBreak(0L, 0, 100, "내용", null));
        assertStatus(400, () -> sharedPlans.createBreakMine(member.getId(),
                breakRequest(WEEK.plusDays(1), 0L, 0, 100, "내용", UUID.randomUUID())));
        assertStatus(400, () -> sharedPlans.createBreakMine(member.getId(),
                breakRequest(null, 0L, 0, 100, "내용", UUID.randomUUID())));

        assertThat(sharedWeeks.count()).isZero();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void breakSortOverflowRejectsWithoutWritingOrActivating() {
        legacy(100, 0, "마지막 정렬값", false, Integer.MAX_VALUE);

        assertStatus(400, () -> createBreak(0L, 0, 100, "추가", UUID.randomUUID()));

        assertThat(sharedWeeks.count()).isZero();
        assertThat(receipts.count()).isZero();
        assertThat(legacyItems.findAll()).singleElement().satisfies(item -> assertThat(item.getContent()).isEqualTo("마지막 정렬값"));
    }

    @Test
    void concurrentGoalAndGroupSaveWithSameRevisionAllowExactlyOneWinner() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> goalSave = statusCall(start, () -> sharedPlans.saveGoalMine(member.getId(),
                new SharedPlanGoalRequest(WEEK, 0L, "동시 목표")));
        Callable<Integer> groupSave = statusCall(start, () -> createGroup(0L, "동시 공유 계획"));

        List<Integer> statuses = concurrent(goalSave, groupSave);

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        SharedPlanWeekResponse current = sharedPlans.findMine(member.getId(), WEEK);
        assertThat(current.revision()).isEqualTo(1L);
        assertThat(current.legacySaveProtected()).isTrue();
        assertThat(sharedWeeks.count()).isEqualTo(1L);
        assertThat(weeklyGoals.count() + sharedItems.count()).isEqualTo(1L);
        if (statuses.getFirst() == 200) {
            assertThat(current.goal()).isEqualTo("동시 목표");
            assertThat(allItems(current)).isEmpty();
        } else {
            assertThat(current.goal()).isEmpty();
            assertThat(findContent(current, "동시 공유 계획")).isNotNull();
        }
    }

    @Test
    void concurrentGoalAndBreakSaveWithSameRevisionAllowExactlyOneWinner() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);

        List<Integer> statuses = concurrent(
                statusCall(start, () -> sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, "목표"))),
                statusCall(start, () -> createBreak(0L, 0, 100, "점심", UUID.randomUUID())));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(weeklyGoals.count() + legacyItems.count()).isEqualTo(1L);
        assertThat(receipts.count()).isEqualTo(legacyItems.count());
    }

    @Test
    void concurrentIdenticalBreakCreateReturnsOneStableItemAndOneReceipt() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        BreakPlanCreateRequest request = breakRequest(WEEK, 0L, 0, 100, "동일 점심 요청", UUID.randomUUID());
        Callable<SharedPlanWeekResponse> save = () -> {
            start.await(5, TimeUnit.SECONDS);
            return sharedPlans.createBreakMine(member.getId(), request);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(save);
            var second = executor.submit(save);
            SharedPlanWeekResponse one = first.get(15, TimeUnit.SECONDS);
            SharedPlanWeekResponse two = second.get(15, TimeUnit.SECONDS);
            assertThat(one.revision()).isEqualTo(1L);
            assertThat(two.revision()).isEqualTo(1L);
            assertThat(findContent(one, "동일 점심 요청").id()).isEqualTo(findContent(two, "동일 점심 요청").id());
        }
        assertThat(legacyItems.count()).isEqualTo(1L);
        assertThat(receipts.count()).isEqualTo(1L);
        assertThat(sharedWeeks.count()).isEqualTo(1L);
    }

    @Test
    void differentMembersAndWeeksKeepIndependentRevisionsAndGoals() {
        Member other = newMember("다른 회원", 13);
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, "내 목표"));

        sharedPlans.saveGoalMine(other.getId(), new SharedPlanGoalRequest(WEEK, 0L, "다른 회원 목표"));
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(OTHER_WEEK, 0L, "다음 주 목표"));

        assertThat(sharedPlans.findMine(member.getId(), WEEK).goal()).isEqualTo("내 목표");
        assertThat(sharedPlans.findMine(other.getId(), WEEK).goal()).isEqualTo("다른 회원 목표");
        assertThat(sharedPlans.findMine(member.getId(), OTHER_WEEK).goal()).isEqualTo("다음 주 목표");
        assertThat(sharedWeeks.findAll()).allSatisfy(week -> assertThat(week.getRevision()).isEqualTo(1L));
    }

    @Test
    void deletedMemberCannotWriteGoalOrBreakData() {
        Long deletedId = member.getId();
        members.deleteById(deletedId);

        assertStatus(404, () -> sharedPlans.saveGoalMine(deletedId, new SharedPlanGoalRequest(WEEK, 0L, "목표")));
        assertStatus(404, () -> sharedPlans.createBreakMine(deletedId,
                breakRequest(WEEK, 0L, 0, 100, "점심", UUID.randomUUID())));

        assertThat(sharedWeeks.count()).isZero();
        assertThat(weeklyGoals.count()).isZero();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void memberPlanCleanupDeletesOnlyThatMembersBreakReceiptsAndPlans() {
        Member other = newMember("보존할 회원", 13);
        createBreak(0L, 0, 100, "내 점심", UUID.randomUUID());
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 1L, "내 목표"));
        UUID otherToken = UUID.randomUUID();
        sharedPlans.createBreakMine(other.getId(), breakRequest(WEEK, 0L, 0, 101, "보존할 저녁", otherToken));

        legacyPlans.deleteAllByMemberId(member.getId());

        assertThat(receipts.count()).isEqualTo(1L);
        assertThat(receipts.findByMemberIdAndClientRequestId(other.getId(), otherToken)).isPresent();
        assertThat(legacyItems.findAll()).singleElement().satisfies(item -> assertThat(item.getMemberId()).isEqualTo(other.getId()));
        assertThat(weeklyGoals.count()).isZero();
        assertThat(sharedWeeks.findAll()).singleElement().satisfies(week -> assertThat(week.getMemberId()).isEqualTo(other.getId()));
    }

    @Test
    void weeklyGoalAndBreakPlanLifecycleDoesNotChangeNonzeroStudyTotalsOrBreakSessions() {
        StudyPresenceSession presence = StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(),
                Instant.parse("2026-10-26T00:00:00Z"));
        presence.checkOut(Instant.parse("2026-10-26T02:00:00Z"));
        presence = presences.saveAndFlush(presence);
        StudyBreakSession session = new StudyBreakSession(presence.getId(), member.getId(), branch.getId(), WEEK,
                StudyBreak.AFTER_FIRST, Instant.parse("2026-10-26T01:30:00Z"),
                Instant.parse("2026-10-26T01:45:00Z"), Instant.parse("2026-10-26T01:31:00Z"));
        session.stopByMember(Instant.parse("2026-10-26T01:40:00Z"));
        session = breaks.saveAndFlush(session);
        var before = studyReports.findMine(member.getId(), WEEK, WEEK);
        assertThat(before.totals().recognizedPeriodDuration().totalSeconds()).isEqualTo(105 * 60);
        assertThat(before.totals().recognizedBreakDuration().totalSeconds()).isEqualTo(9 * 60);

        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 0L, "공부 목표"));
        Long lunchId = findContent(createBreak(1L, 0, 100, "점심 계획", UUID.randomUUID()), "점심 계획").id();
        Long dinnerId = findContent(createBreak(2L, 0, 101, "저녁 계획", UUID.randomUUID()), "저녁 계획").id();
        sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY, lunchId,
                new SharedPlanUpdateRequest(WEEK, 3L, "완료한 점심 계획", true));
        sharedPlans.deleteMine(member.getId(), PlanItemSource.LEGACY, dinnerId, WEEK, 4L);
        sharedPlans.saveGoalMine(member.getId(), new SharedPlanGoalRequest(WEEK, 5L, ""));

        assertThat(studyReports.findMine(member.getId(), WEEK, WEEK)).isEqualTo(before);
        assertThat(presences.findById(presence.getId()).orElseThrow().getCheckedOutAt())
                .isEqualTo(Instant.parse("2026-10-26T02:00:00Z"));
        assertThat(breaks.findById(session.getId()).orElseThrow().getEndedAt())
                .isEqualTo(Instant.parse("2026-10-26T01:40:00Z"));
        assertThat(presences.count()).isEqualTo(1L);
        assertThat(breaks.count()).isEqualTo(1L);
    }

    @Test
    void newHttpEndpointsRequireAuthentication() throws Exception {
        mvc.perform(patch("/api/weekly-plans/shared/me/goal").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/weekly-plans/shared/me/break-items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(sharedWeeks.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    @Test
    void httpGoalAndBreakWritesUseAuthenticatedOwnerAndExposeRevisionConflicts() throws Exception {
        Member other = newMember("요청 본문 속 타인", 13);
        mvc.perform(patch("/api/weekly-plans/shared/me/goal")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":0,"goal":"내 주간 목표",
                                 "memberId":%d,"branchId":999999}
                                """.formatted(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.goal").value("내 주간 목표"))
                .andExpect(jsonPath("$.revision").value(1));
        mvc.perform(post("/api/weekly-plans/shared/me/break-items")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":1,"dayIndex":0,
                                 "periodIndex":100,"content":"HTTP 점심","clientRequestId":"%s",
                                 "memberId":%d,"branchId":999999}
                                """.formatted(UUID.randomUUID(), other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.ungroupedItems[0].source").value("LEGACY"))
                .andExpect(jsonPath("$.ungroupedItems[0].periodIndex").value(100));
        mvc.perform(patch("/api/weekly-plans/shared/me/goal")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":1,"goal":"오래된 목표"}
                                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(sharedPlans.findMine(other.getId(), WEEK).revision()).isZero();
        assertThat(sharedPlans.findMine(other.getId(), WEEK).goal()).isEmpty();
        assertThat(allItems(sharedPlans.findMine(other.getId(), WEEK))).isEmpty();
    }

    @Test
    void httpForeignBreakPlanCannotBeEditedOrDeleted() throws Exception {
        Member other = newMember("다른 회원", 13);
        SharedPlanWeekResponse otherCreated = sharedPlans.createBreakMine(other.getId(),
                breakRequest(WEEK, 0L, 0, 100, "다른 회원 점심", UUID.randomUUID()));
        Long foreignId = findContent(otherCreated, "다른 회원 점심").id();

        mvc.perform(patch("/api/weekly-plans/shared/me/items/LEGACY/{id}", foreignId)
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":0,"done":true}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/weekly-plans/shared/me/items/LEGACY/{id}", foreignId)
                        .header("Authorization", bearer(member)).param("weekStartDate", WEEK.toString())
                        .param("expectedRevision", "0"))
                .andExpect(status().isNotFound());

        assertThat(legacyItems.findById(foreignId).orElseThrow().isDone()).isFalse();
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isZero();
        assertThat(sharedPlans.findMine(other.getId(), WEEK).revision()).isEqualTo(1L);
    }

    @Test
    void httpInvalidGoalOrBreakPayloadReturnsBadRequestWithoutActivation() throws Exception {
        mvc.perform(patch("/api/weekly-plans/shared/me/goal")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":0,"goal":null}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/weekly-plans/shared/me/break-items")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":0,"dayIndex":0,
                                 "periodIndex":1,"content":"정규 교시를 점심으로 추가하지 않기","clientRequestId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(sharedWeeks.count()).isZero();
        assertThat(weeklyGoals.count()).isZero();
        assertThat(legacyItems.count()).isZero();
        assertThat(receipts.count()).isZero();
    }

    private Member newMember(String name, Integer seat) {
        return members.saveAndFlush(new Member(branch.getId(), name, "test-password", MemberRole.MEMBER,
                seat, WEEK.minusMonths(1), 3L));
    }

    private WeeklyPlanItem legacy(int period, int day, String content, boolean done, int order) {
        return legacyItems.saveAndFlush(new WeeklyPlanItem(member.getId(), branch.getId(), WEEK,
                period, day, content, done, order));
    }

    private SharedPlanWeekResponse createGroup(Long revision, String content) {
        return sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, revision, 0, PlanGroup.PERIODS_1_2, content, UUID.randomUUID()));
    }

    private SharedPlanWeekResponse createBreak(Long revision, int day, int period, String content, UUID token) {
        return sharedPlans.createBreakMine(member.getId(), breakRequest(WEEK, revision, day, period, content, token));
    }

    private BreakPlanCreateRequest breakRequest(LocalDate week, Long revision, Integer day, Integer period,
                                               String content, UUID token) {
        return new BreakPlanCreateRequest(week, revision, day, period, content, token);
    }

    private SharedPlanItemResponse findContent(SharedPlanWeekResponse response, String content) {
        return allItems(response).stream().filter(item -> item.content().equals(content)).findFirst().orElseThrow();
    }

    private List<SharedPlanItemResponse> allItems(SharedPlanWeekResponse response) {
        List<SharedPlanItemResponse> items = new ArrayList<>();
        response.groups().forEach(group -> items.addAll(group.items()));
        items.addAll(response.ungroupedItems());
        return items;
    }

    private List<String> legacySnapshots() {
        return legacyItems.findAll().stream().map(item -> String.join("|", item.getId().toString(),
                item.getMemberId().toString(), item.getBranchId().toString(), item.getWeekStartDate().toString(),
                Integer.toString(item.getPeriodIndex()), Integer.toString(item.getDayIndex()), item.getContent(),
                Boolean.toString(item.isDone()), Integer.toString(item.getSortOrder()),
                item.getCreatedAt().toString(), item.getUpdatedAt().toString())).sorted().toList();
    }

    private Callable<Integer> statusCall(CyclicBarrier start, Runnable operation) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                operation.run();
                return 200;
            } catch (ResponseStatusException error) {
                return error.getStatusCode().value();
            }
        };
    }

    private List<Integer> concurrent(Callable<Integer> first, Callable<Integer> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(first);
            var two = executor.submit(second);
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        }
    }

    private void assertStatus(int expected, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(expected));
    }

    private String bearer(Member authenticatedMember) {
        return "Bearer " + jwt.createAccessToken(authenticatedMember);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock sharedPlanCompatibilityTestClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
