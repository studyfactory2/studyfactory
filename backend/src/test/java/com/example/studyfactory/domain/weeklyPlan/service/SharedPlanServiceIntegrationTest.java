package com.example.studyfactory.domain.weeklyPlan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.example.studyfactory.domain.weeklyPlan.dto.MonthlyPlanGoalRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanCreateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanItemResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanUpdateRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.SharedPlanWeekResponse;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanItemRequest;
import com.example.studyfactory.domain.weeklyPlan.dto.WeeklyPlanSaveRequest;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import com.example.studyfactory.domain.weeklyPlan.model.PlanItemSource;
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

/**
 * Real JPA transactions against a private H2 database. These tests do not run SQL
 * migrations, connect to PostgreSQL, or mutate any application/user database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shared-plan-save-safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000",
        "study-break.reconciliation.enabled=false",
        "study-presence.auto-close.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("공유 교시 계획 저장 안전성 통합 테스트")
class SharedPlanServiceIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 26);
    private static final LocalDate OTHER_WEEK = WEEK.plusWeeks(1);
    private static final Instant NOW = Instant.parse("2026-10-26T13:30:00Z");

    @Autowired private SharedPlanService sharedPlans;
    @Autowired private WeeklyPlanService legacyPlans;
    @Autowired private SharedPlanItemRepository sharedItems;
    @Autowired private SharedPlanWeekRepository sharedWeeks;
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
        sharedItems.deleteAll();
        sharedWeeks.deleteAll();
        legacyItems.deleteAll();
        weeklyGoals.deleteAll();
        monthlyGoals.deleteAll();
        members.deleteAll();
        branches.deleteAll();
        branch = branches.saveAndFlush(new Branch("망미점", "부산"));
        member = newMember(branch, MemberRole.MEMBER, "김회원", 12);
    }

    @Test
    void readsPreserveEveryLegacyRecordWithoutActivationOrRewriting() {
        List<WeeklyPlanItem> original = new ArrayList<>();
        for (int period = 0; period < 7; period++) {
            original.add(legacy(period, period, "기존 " + period, period % 2 == 0, 9 - period));
        }
        original.add(legacy(100, 0, "점심 계획", false, 8));
        original.add(legacy(101, 6, "저녁 계획", true, 3));
        original.add(legacy(42, 0, "기타 옛 계획", true, 7));
        original.add(legacy(0, 9, "범위 밖 날짜도 보존", false, 2));
        original.add(legacy(1, 0, "", false, 4));
        List<String> before = legacySnapshots();
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), WEEK, "기존 목표"));

        SharedPlanWeekResponse response = sharedPlans.findMine(member.getId(), WEEK);

        assertThat(response.revision()).isZero();
        assertThat(response.legacySaveProtected()).isFalse();
        assertThat(response.goal()).isEqualTo("기존 목표");
        assertThat(response.groups()).extracting(group -> group.group())
                .containsExactly(PlanGroup.PERIODS_1_2, PlanGroup.PERIODS_3_5, PlanGroup.PERIODS_6_7);
        assertThat(groupItems(response, PlanGroup.PERIODS_1_2))
                .extracting(SharedPlanItemResponse::periodIndex).containsExactlyInAnyOrder(0, 1, 1);
        assertThat(groupItems(response, PlanGroup.PERIODS_3_5))
                .extracting(SharedPlanItemResponse::periodIndex).containsExactlyInAnyOrder(2, 3, 4);
        assertThat(groupItems(response, PlanGroup.PERIODS_6_7))
                .extracting(SharedPlanItemResponse::periodIndex).containsExactlyInAnyOrder(5, 6);
        assertThat(response.ungroupedItems()).extracting(SharedPlanItemResponse::id)
                .containsExactlyInAnyOrderElementsOf(original.subList(7, 11).stream().map(WeeklyPlanItem::getId).toList());
        assertThat(allItems(response)).extracting(SharedPlanItemResponse::id)
                .containsExactlyInAnyOrderElementsOf(original.stream().map(WeeklyPlanItem::getId).toList());
        assertThat(allItems(response)).allSatisfy(item -> assertThat(item.source()).isEqualTo(PlanItemSource.LEGACY));
        assertThat(legacySnapshots()).isEqualTo(before);
        assertThat(sharedItems.count()).isZero();
        assertThat(sharedWeeks.count()).isZero();
    }

    @Test
    void createsOneTrueSharedRecordAndDoesNotCopyOrMoveExistingPlans() {
        WeeklyPlanItem existing = legacy(4, 2, "기존 5교시 계획", true, 8);
        List<String> before = legacySnapshots();

        SharedPlanWeekResponse response = create(0L, 2, PlanGroup.PERIODS_3_5, "  민법 3장 복습  ");

        assertThat(response.revision()).isEqualTo(1L);
        assertThat(response.legacySaveProtected()).isTrue();
        assertThat(sharedItems.count()).isEqualTo(1);
        assertThat(legacySnapshots()).isEqualTo(before);
        assertThat(groupItems(response, PlanGroup.PERIODS_3_5)).hasSize(2);
        SharedPlanItemResponse added = allItems(response).stream()
                .filter(item -> item.source() == PlanItemSource.GROUP).findFirst().orElseThrow();
        assertThat(added.group()).isEqualTo(PlanGroup.PERIODS_3_5);
        assertThat(added.periodIndex()).isNull();
        assertThat(added.dayIndex()).isEqualTo(2);
        assertThat(added.content()).isEqualTo("민법 3장 복습");
        assertThat(added.done()).isFalse();
        assertThat(legacyItems.findById(existing.getId())).isPresent();
    }

    @Test
    void targetedLegacyEditPreservesIdPeriodDayOrderOtherDatesAndGoals() {
        WeeklyPlanItem target = legacy(4, 2, "기존 내용", false, 8);
        WeeklyPlanItem untouched = legacy(3, 2, "다른 교시", true, 3);
        WeeklyPlanItem anotherWeek = legacyItems.saveAndFlush(new WeeklyPlanItem(
                member.getId(), branch.getId(), OTHER_WEEK, 4, 2, "다음 주", false, 1));
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), WEEK, "주간 목표"));
        legacyPlans.saveMonthlyGoal(member.getId(), new MonthlyPlanGoalRequest("2026-10", "월간 목표"));

        SharedPlanWeekResponse edited = sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY,
                target.getId(), new SharedPlanUpdateRequest(WEEK, 0L, "수정한 내용", true));

        assertThat(edited.revision()).isEqualTo(1L);
        assertThat(edited.legacySaveProtected()).isTrue();
        WeeklyPlanItem persisted = legacyItems.findById(target.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(target.getId());
        assertThat(persisted.getPeriodIndex()).isEqualTo(4);
        assertThat(persisted.getDayIndex()).isEqualTo(2);
        assertThat(persisted.getSortOrder()).isEqualTo(8);
        assertThat(persisted.getContent()).isEqualTo("수정한 내용");
        assertThat(persisted.isDone()).isTrue();
        assertThat(legacyItems.findById(untouched.getId()).orElseThrow().getContent()).isEqualTo("다른 교시");
        assertThat(legacyItems.findById(anotherWeek.getId()).orElseThrow().getContent()).isEqualTo("다음 주");
        assertThat(edited.goal()).isEqualTo("주간 목표");
        assertThat(legacyPlans.findMonthlyGoal(member.getId(), "2026-10").goal()).isEqualTo("월간 목표");
    }

    @Test
    void completionOnlyPreservesBlankLegacyTextAndUnknownPeriod() {
        WeeklyPlanItem target = legacy(100, 0, "", false, 6);

        SharedPlanWeekResponse response = sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY,
                target.getId(), new SharedPlanUpdateRequest(WEEK, 0L, null, true));

        assertThat(response.ungroupedItems()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(target.getId());
            assertThat(item.periodIndex()).isEqualTo(100);
            assertThat(item.content()).isEmpty();
            assertThat(item.done()).isTrue();
        });
    }

    @Test
    void staleUpdateDeleteAndCreateCannotOverwriteCommittedData() {
        SharedPlanWeekResponse initial = create(0L, 0, PlanGroup.PERIODS_1_2, "첫 기록");
        Long id = onlySharedId(initial);
        sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "먼저 저장된 수정", true));

        assertStatus(409, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "오래된 수정", false)));
        assertStatus(409, () -> sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP, id, WEEK, 1L));
        assertStatus(409, () -> create(1L, 0, PlanGroup.PERIODS_1_2, "오래된 추가"));

        SharedPlanWeekResponse current = sharedPlans.findMine(member.getId(), WEEK);
        assertThat(current.revision()).isEqualTo(2L);
        assertThat(allItems(current)).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("먼저 저장된 수정");
            assertThat(item.done()).isTrue();
        });
        assertThat(sharedItems.count()).isEqualTo(1);
    }

    @Test
    void matchingCreateRetryIsIdempotentAfterLaterEditAndDeletion() {
        UUID requestId = UUID.randomUUID();
        SharedPlanCreateRequest request = new SharedPlanCreateRequest(WEEK, 0L, 0,
                PlanGroup.PERIODS_1_2, "원래 추가 내용", requestId);
        SharedPlanWeekResponse created = sharedPlans.createMine(member.getId(), request);
        Long id = onlySharedId(created);
        sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "나중에 수정함", true));

        SharedPlanWeekResponse retried = sharedPlans.createMine(member.getId(), request);
        assertThat(retried.revision()).isEqualTo(2L);
        assertThat(allItems(retried)).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.content()).isEqualTo("나중에 수정함");
        });
        sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP, id, WEEK, 2L);
        SharedPlanWeekResponse afterDeletedRetry = sharedPlans.createMine(member.getId(), request);
        assertThat(afterDeletedRetry.revision()).isEqualTo(3L);
        assertThat(allItems(afterDeletedRetry)).isEmpty();
        assertThat(afterDeletedRetry.legacySaveProtected()).isTrue();
        assertThat(sharedItems.count()).isEqualTo(1); // Original request token remains in its tombstone.

        assertStatus(409, () -> sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, 3L, 0, PlanGroup.PERIODS_1_2, "같은 요청 번호지만 다른 내용", requestId)));
        assertStatus(409, () -> sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                OTHER_WEEK, 0L, 0, PlanGroup.PERIODS_1_2, "원래 추가 내용", requestId)));
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(3L);
        assertThat(sharedPlans.findMine(member.getId(), OTHER_WEEK).revision()).isZero();
    }

    @Test
    void activatedWeekRejectsLegacyWholeWeekSaveBeforeGoalOrRowsChange() {
        WeeklyPlanItem original = legacy(0, 0, "중요한 기존 계획", true, 4);
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), WEEK, "보존할 목표"));
        create(0L, 0, PlanGroup.PERIODS_1_2, "공유 계획");
        List<String> before = legacySnapshots();

        assertStatus(409, () -> legacyPlans.saveMine(member.getId(),
                new WeeklyPlanSaveRequest(WEEK, "오래된 탭의 목표", List.of())));

        assertThat(legacySnapshots()).isEqualTo(before);
        assertThat(legacyItems.findById(original.getId())).isPresent();
        assertThat(legacyPlans.findMine(member.getId(), WEEK).goal()).isEqualTo("보존할 목표");
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(allItems(sharedPlans.findMine(member.getId(), WEEK))).hasSize(2);
    }

    @Test
    void untouchedLegacyWeekStillSavesAndInvalidatesAnOlderSharedSnapshot() {
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isZero();
        legacyPlans.saveMine(member.getId(), new WeeklyPlanSaveRequest(WEEK, "기존 화면 저장", List.of(
                new WeeklyPlanItemRequest(5, 1, "구버전에서 저장함", true, 6))));

        SharedPlanWeekResponse afterLegacySave = sharedPlans.findMine(member.getId(), WEEK);
        assertThat(afterLegacySave.revision()).isEqualTo(1L);
        assertThat(afterLegacySave.legacySaveProtected()).isFalse();
        assertStatus(409, () -> create(0L, 1, PlanGroup.PERIODS_6_7, "오래된 화면"));
        legacyPlans.saveMine(member.getId(), new WeeklyPlanSaveRequest(WEEK, "기존 화면 재저장", List.of(
                new WeeklyPlanItemRequest(5, 1, "계속 사용 가능", false, 0))));
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(2L);
        assertThat(sharedPlans.findMine(member.getId(), WEEK).legacySaveProtected()).isFalse();
        assertThat(legacyPlans.findMine(member.getId(), WEEK).items()).singleElement()
                .satisfies(item -> assertThat(item.content()).isEqualTo("계속 사용 가능"));
    }

    @Test
    void onlyExplicitlyDeletedLegacyItemDisappearsAndProtectionRemains() {
        WeeklyPlanItem target = legacy(0, 0, "삭제할 항목", false, 0);
        WeeklyPlanItem other = legacy(1, 0, "보존할 항목", true, 3);

        SharedPlanWeekResponse deleted = sharedPlans.deleteMine(member.getId(), PlanItemSource.LEGACY,
                target.getId(), WEEK, 0L);

        assertThat(deleted.revision()).isEqualTo(1L);
        assertThat(deleted.legacySaveProtected()).isTrue();
        assertThat(legacyItems.findById(target.getId())).isEmpty();
        assertThat(legacyItems.findById(other.getId())).isPresent();
        assertThat(allItems(deleted)).extracting(SharedPlanItemResponse::id).containsExactly(other.getId());
    }

    @Test
    void validationAndMissingTargetsDoNotCreateStateOrActivateWeek() {
        assertStatus(400, () -> sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK.plusDays(1), 0L, 0, PlanGroup.PERIODS_1_2, "잘못된 주 시작일", UUID.randomUUID())));
        assertStatus(400, () -> create(0L, 7, PlanGroup.PERIODS_1_2, "잘못된 요일"));
        assertStatus(400, () -> create(0L, 0, null, "잘못된 그룹"));
        assertStatus(400, () -> create(null, 0, PlanGroup.PERIODS_1_2, "버전 없음"));
        assertStatus(400, () -> create(-1L, 0, PlanGroup.PERIODS_1_2, "음수 버전"));
        assertStatus(400, () -> create(0L, 0, PlanGroup.PERIODS_1_2, "  "));
        assertStatus(400, () -> sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, 0L, 0, PlanGroup.PERIODS_1_2, "요청 번호 없음", null)));
        assertStatus(404, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY,
                Long.MAX_VALUE, new SharedPlanUpdateRequest(WEEK, 0L, "없는 항목", null)));
        assertStatus(404, () -> sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP,
                Long.MAX_VALUE, WEEK, 0L));
        assertThat(sharedWeeks.count()).isZero();
        assertThat(sharedItems.count()).isZero();
    }

    @Test
    void rejectedEditsDoNotIncrementRevisionOrAlterText() {
        Long id = onlySharedId(create(0L, 0, PlanGroup.PERIODS_1_2, "보존할 내용"));
        assertStatus(400, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "  ", true)));
        assertStatus(400, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, null, null)));
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(allItems(sharedPlans.findMine(member.getId(), WEEK))).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("보존할 내용");
            assertThat(item.done()).isFalse();
        });
    }

    @Test
    void foreignMemberAndWrongWeekTargetsCannotBeChanged() {
        Member other = newMember(branch, MemberRole.MEMBER, "다른 회원", 13);
        WeeklyPlanItem foreignLegacy = legacyItems.saveAndFlush(new WeeklyPlanItem(
                other.getId(), branch.getId(), WEEK, 0, 0, "다른 회원 기록", false, 0));
        Long foreignSharedId = onlySharedId(sharedPlans.createMine(other.getId(), new SharedPlanCreateRequest(
                WEEK, 0L, 0, PlanGroup.PERIODS_1_2, "다른 회원 공유 기록", UUID.randomUUID())));
        Long ownSharedId = onlySharedId(create(0L, 0, PlanGroup.PERIODS_1_2, "내 공유 기록"));

        assertStatus(404, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.LEGACY,
                foreignLegacy.getId(), new SharedPlanUpdateRequest(WEEK, 1L, "침범", true)));
        assertStatus(404, () -> sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP,
                foreignSharedId, WEEK, 1L));
        assertStatus(404, () -> sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP,
                ownSharedId, new SharedPlanUpdateRequest(OTHER_WEEK, 0L, "잘못된 주", null)));
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(sharedPlans.findMine(member.getId(), OTHER_WEEK).revision()).isZero();
        assertThat(sharedPlans.findMine(other.getId(), WEEK).revision()).isEqualTo(1L);
        assertThat(legacyItems.findById(foreignLegacy.getId()).orElseThrow().getContent()).isEqualTo("다른 회원 기록");
    }

    @Test
    void managerReadAccessKeepsExistingRoleAndBranchBoundaries() {
        Member staff = newMember(branch, MemberRole.STAFF, "같은 지점 직원", 14);
        Member admin = newMember(branch, MemberRole.ADMIN, "관리자", null);
        Branch otherBranch = branches.saveAndFlush(new Branch("다른 지점", "서울"));
        Member foreign = newMember(otherBranch, MemberRole.MEMBER, "다른 지점 회원", 2);
        Member foreignStaff = newMember(otherBranch, MemberRole.STAFF, "다른 지점 직원", 3);

        assertThat(sharedPlans.findForManager(staff.getId(), member.getId(), WEEK).memberId()).isEqualTo(member.getId());
        assertStatus(403, () -> sharedPlans.findForManager(member.getId(), staff.getId(), WEEK));
        assertStatus(403, () -> sharedPlans.findForManager(staff.getId(), foreign.getId(), WEEK));
        assertStatus(403, () -> sharedPlans.findForManager(staff.getId(), admin.getId(), WEEK));
        assertThat(sharedPlans.findForManager(admin.getId(), foreign.getId(), WEEK).memberId()).isEqualTo(foreign.getId());
        assertThat(sharedPlans.findForManager(admin.getId(), foreignStaff.getId(), WEEK).memberId()).isEqualTo(foreignStaff.getId());
        assertThat(sharedWeeks.count()).isZero();
    }

    @Test
    void concurrentInitialWritesSerializeAndOnlyOneRevisionZeroSaveWins() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> write = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                create(0L, 0, PlanGroup.PERIODS_1_2, "동시 저장 " + Thread.currentThread().threadId());
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(write);
            var second = executor.submit(write);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }

        assertThat(sharedWeeks.count()).isEqualTo(1);
        assertThat(sharedItems.count()).isEqualTo(1);
        assertThat(sharedPlans.findMine(member.getId(), WEEK).revision()).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicateCreateRequestsReturnOnePersistedItem() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        SharedPlanCreateRequest sameRequest = new SharedPlanCreateRequest(WEEK, 0L, 0,
                PlanGroup.PERIODS_1_2, "재전송된 같은 계획", UUID.randomUUID());
        Callable<SharedPlanWeekResponse> write = () -> {
            start.await(5, TimeUnit.SECONDS);
            return sharedPlans.createMine(member.getId(), sameRequest);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(write);
            var second = executor.submit(write);
            SharedPlanWeekResponse firstResponse = first.get(15, TimeUnit.SECONDS);
            SharedPlanWeekResponse secondResponse = second.get(15, TimeUnit.SECONDS);
            assertThat(firstResponse.revision()).isEqualTo(1L);
            assertThat(secondResponse.revision()).isEqualTo(1L);
            assertThat(onlySharedId(firstResponse)).isEqualTo(onlySharedId(secondResponse));
        }
        assertThat(sharedItems.count()).isEqualTo(1);
        assertThat(sharedWeeks.count()).isEqualTo(1);
    }

    @Test
    void concurrentLegacyAndSharedFirstSaveCannotSilentlyReplaceEachOther() throws Exception {
        WeeklyPlanItem existing = legacy(0, 0, "두 화면에서 읽은 계획", false, 0);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> oldSave = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                legacyPlans.saveMine(member.getId(), new WeeklyPlanSaveRequest(WEEK, "구버전 목표", List.of(
                        new WeeklyPlanItemRequest(0, 0, "구버전에서 고친 계획", true, 0))));
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        Callable<Integer> sharedSave = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                create(0L, 0, PlanGroup.PERIODS_1_2, "새 공유 계획");
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };

        int oldStatus;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var oldResult = executor.submit(oldSave);
            var sharedResult = executor.submit(sharedSave);
            oldStatus = oldResult.get(15, TimeUnit.SECONDS);
            assertThat(List.of(oldStatus, sharedResult.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        SharedPlanWeekResponse current = sharedPlans.findMine(member.getId(), WEEK);
        assertThat(current.revision()).isEqualTo(1L);
        assertThat(sharedWeeks.count()).isEqualTo(1);
        if (oldStatus == 200) {
            assertThat(current.legacySaveProtected()).isFalse();
            assertThat(sharedItems.count()).isZero();
            assertThat(allItems(current)).singleElement()
                    .satisfies(item -> assertThat(item.content()).isEqualTo("구버전에서 고친 계획"));
        } else {
            assertThat(current.legacySaveProtected()).isTrue();
            assertThat(current.goal()).isEmpty();
            assertThat(sharedItems.count()).isEqualTo(1);
            assertThat(legacyItems.findById(existing.getId()).orElseThrow().getContent()).isEqualTo("두 화면에서 읽은 계획");
        }
    }

    @Test
    void memberCleanupDeletesNewStorageWithoutTouchingAnotherMember() {
        legacy(0, 0, "기존 계획", false, 0);
        weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), WEEK, "목표"));
        legacyPlans.saveMonthlyGoal(member.getId(), new MonthlyPlanGoalRequest("2026-10", "월간 목표"));
        create(0L, 0, PlanGroup.PERIODS_1_2, "내 공유 계획");
        Member other = newMember(branch, MemberRole.MEMBER, "보존할 회원", 15);
        sharedPlans.createMine(other.getId(), new SharedPlanCreateRequest(
                WEEK, 0L, 0, PlanGroup.PERIODS_1_2, "보존할 공유 계획", UUID.randomUUID()));

        legacyPlans.deleteAllByMemberId(member.getId());

        assertThat(legacyItems.count()).isZero();
        assertThat(weeklyGoals.count()).isZero();
        assertThat(monthlyGoals.count()).isZero();
        assertThat(sharedItems.findAll()).singleElement().satisfies(item -> assertThat(item.getMemberId()).isEqualTo(other.getId()));
        assertThat(sharedWeeks.findAll()).singleElement().satisfies(week -> assertThat(week.getMemberId()).isEqualTo(other.getId()));
    }

    @Test
    void checklistCompletionEditingAndDeletionDoNotChangeStudyTimeOrBreakRecords() {
        StudyPresenceSession presence = StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(),
                Instant.parse("2026-10-26T00:00:00Z"));
        presence.checkOut(Instant.parse("2026-10-26T02:00:00Z"));
        presence = presences.saveAndFlush(presence);
        StudyBreakSession breakSession = new StudyBreakSession(presence.getId(), member.getId(), branch.getId(), WEEK,
                StudyBreak.AFTER_FIRST, Instant.parse("2026-10-26T01:30:00Z"),
                Instant.parse("2026-10-26T01:45:00Z"), Instant.parse("2026-10-26T01:31:00Z"));
        breakSession.stopByMember(Instant.parse("2026-10-26T01:40:00Z"));
        breakSession = breaks.saveAndFlush(breakSession);
        var before = studyReports.findMine(member.getId(), WEEK, WEEK);
        assertThat(before.totals().recognizedPeriodDuration().totalSeconds()).isEqualTo(105 * 60);
        assertThat(before.totals().recognizedBreakDuration().totalSeconds()).isEqualTo(9 * 60);
        Long id = onlySharedId(create(0L, 0, PlanGroup.PERIODS_1_2, "학습 계획"));

        sharedPlans.updateMine(member.getId(), PlanItemSource.GROUP, id,
                new SharedPlanUpdateRequest(WEEK, 1L, "완료한 계획", true));
        sharedPlans.deleteMine(member.getId(), PlanItemSource.GROUP, id, WEEK, 2L);

        assertThat(studyReports.findMine(member.getId(), WEEK, WEEK)).isEqualTo(before);
        assertThat(presences.findById(presence.getId())).isPresent();
        assertThat(breaks.findById(breakSession.getId()).orElseThrow().getEndedAt())
                .isEqualTo(Instant.parse("2026-10-26T01:40:00Z"));
        assertThat(presences.count()).isEqualTo(1);
        assertThat(breaks.count()).isEqualTo(1);
    }

    @Test
    void sharedHttpEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/weekly-plans/shared/me").param("weekStartDate", WEEK.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/weekly-plans/shared/members/{id}", member.getId())
                        .param("weekStartDate", WEEK.toString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/weekly-plans/shared/me/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/weekly-plans/shared/me/items/GROUP/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/weekly-plans/shared/me/items/GROUP/1")
                        .param("weekStartDate", WEEK.toString()).param("expectedRevision", "0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void httpCreateUsesAuthenticatedMemberAndReturnsConflictForStaleSave() throws Exception {
        Member other = newMember(branch, MemberRole.MEMBER, "요청 본문 속 타인", 13);
        String body = """
                {"weekStartDate":"2026-10-26","expectedRevision":0,"dayIndex":1,
                 "group":"PERIODS_3_5","content":"HTTP 공유 계획","clientRequestId":"%s",
                 "memberId":%d,"branchId":999999}
                """.formatted(UUID.randomUUID(), other.getId());
        mvc.perform(post("/api/weekly-plans/shared/me/items")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.branchId").value(branch.getId()))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.legacySaveProtected").value(true));
        Long id = onlySharedId(sharedPlans.findMine(member.getId(), WEEK));
        mvc.perform(patch("/api/weekly-plans/shared/me/items/GROUP/{id}", id)
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-26","expectedRevision":0,"done":true}
                                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(allItems(sharedPlans.findMine(other.getId(), WEEK))).isEmpty();
    }

    private Member newMember(Branch owningBranch, MemberRole role, String name, Integer seat) {
        return members.saveAndFlush(new Member(owningBranch.getId(), name, "test-password", role,
                seat, WEEK.minusMonths(1), 3L));
    }

    private WeeklyPlanItem legacy(int period, int day, String content, boolean done, int sortOrder) {
        return legacyItems.saveAndFlush(new WeeklyPlanItem(member.getId(), branch.getId(), WEEK,
                period, day, content, done, sortOrder));
    }

    private SharedPlanWeekResponse create(Long revision, int day, PlanGroup group, String content) {
        return sharedPlans.createMine(member.getId(), new SharedPlanCreateRequest(
                WEEK, revision, day, group, content, UUID.randomUUID()));
    }

    private List<SharedPlanItemResponse> groupItems(SharedPlanWeekResponse response, PlanGroup group) {
        return response.groups().stream().filter(value -> value.group() == group).findFirst().orElseThrow().items();
    }

    private List<SharedPlanItemResponse> allItems(SharedPlanWeekResponse response) {
        List<SharedPlanItemResponse> items = new ArrayList<>();
        response.groups().forEach(group -> items.addAll(group.items()));
        items.addAll(response.ungroupedItems());
        return items;
    }

    private Long onlySharedId(SharedPlanWeekResponse response) {
        return allItems(response).stream().filter(item -> item.source() == PlanItemSource.GROUP)
                .map(SharedPlanItemResponse::id).findFirst().orElseThrow();
    }

    private List<String> legacySnapshots() {
        return legacyItems.findAll().stream().map(item -> String.join("|", item.getId().toString(),
                item.getMemberId().toString(), item.getBranchId().toString(), item.getWeekStartDate().toString(),
                Integer.toString(item.getPeriodIndex()), Integer.toString(item.getDayIndex()), item.getContent(),
                Boolean.toString(item.isDone()), Integer.toString(item.getSortOrder()),
                item.getCreatedAt().toString(), item.getUpdatedAt().toString())).sorted().toList();
    }

    private void assertStatus(int expectedStatus, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(expectedStatus));
    }

    private String bearer(Member authenticatedMember) {
        return "Bearer " + jwt.createAccessToken(authenticatedMember);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock sharedPlanTestClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
