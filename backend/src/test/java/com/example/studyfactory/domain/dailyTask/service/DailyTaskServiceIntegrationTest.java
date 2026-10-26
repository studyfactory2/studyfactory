package com.example.studyfactory.domain.dailyTask.service;

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
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCalendarDayResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCreateRequest;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskDayResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskItemResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskUpdateRequest;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskDayRepository;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskItemRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.service.StudyTimeReportService;
import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoPriority;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import com.example.studyfactory.domain.todo.repository.TodoItemRepository;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanGoalRepository;
import com.example.studyfactory.domain.weeklyPlan.repository.WeeklyPlanItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Real service transactions and HTTP requests against a private in-memory H2 database.
 * This suite never runs migrations or connects to an application or production database.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:daily-task-save-safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000",
        "study-break.reconciliation.enabled=false",
        "study-presence.auto-close.enabled=false"
})
@AutoConfigureMockMvc
@DisplayName("회원 개인 할 일 저장 안전성 통합 테스트")
class DailyTaskServiceIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);
    private static final Instant NOW = Instant.parse("2026-10-26T13:30:00Z");
    private static final String BASE = "/api/member-tasks/me";
    private static final long MAX_SAFE_REVISION = 9_007_199_254_740_991L;

    @Autowired private DailyTaskService tasks;
    @Autowired private DailyTaskItemRepository items;
    @Autowired private DailyTaskDayRepository days;
    @Autowired private MemberRepository members;
    @Autowired private BranchRepository branches;
    @Autowired private WeeklyPlanItemRepository weeklyItems;
    @Autowired private WeeklyPlanGoalRepository weeklyGoals;
    @Autowired private TodoItemRepository staffTodos;
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
        items.deleteAll();
        days.deleteAll();
        weeklyItems.deleteAll();
        weeklyGoals.deleteAll();
        staffTodos.deleteAll();
        members.deleteAll();
        branches.deleteAll();
        branch = branches.saveAndFlush(new Branch("망미점", "부산"));
        member = newMember(branch, MemberRole.MEMBER, "개인 할 일 회원", 12);
    }

    @Test
    void emptyReadsReturnCanonicalOwnerDateAndZeroRevisionWithoutWritingRows() {
        DailyTaskDayResponse response = tasks.findMine(member.getId(), DATE);

        assertThat(response.memberId()).isEqualTo(member.getId());
        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.revision()).isZero();
        assertThat(response.items()).isEmpty();
        var calendar = tasks.findCalendarMine(member.getId(), "2026-10");
        assertThat(calendar.memberId()).isEqualTo(member.getId());
        assertThat(calendar.month()).isEqualTo("2026-10");
        assertThat(calendar.days()).hasSize(31);
        assertThat(calendar.days().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(calendar.days().getLast().date()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(calendar.days()).allSatisfy(day -> {
            assertThat(day.totalCount()).isZero();
            assertThat(day.completedCount()).isZero();
        });
        assertThat(items.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void targetedCrudKeepsOrderingOtherTasksAndOtherDatesUnchanged() {
        DailyTaskDayResponse first = create(member, DATE, 0L, "  주민센터 서류 준비  ");
        DailyTaskItemResponse initialItem = first.items().getFirst();
        assertThat(first.revision()).isEqualTo(1L);
        assertThat(initialItem.content()).isEqualTo("주민센터 서류 준비");
        assertThat(initialItem.completed()).isFalse();
        DailyTaskDayResponse second = create(member, DATE, 1L, "책 반납");
        DailyTaskItemResponse untouched = second.items().getLast();
        DailyTaskDayResponse tomorrow = create(member, DATE.plusDays(1), 0L, "내일 전화");

        DailyTaskDayResponse edited = tasks.updateMine(member.getId(), initialItem.id(),
                new DailyTaskUpdateRequest(DATE, 2L, "  서류 발급 완료  ", null));
        assertThat(edited.revision()).isEqualTo(3L);
        assertThat(edited.items().getFirst().id()).isEqualTo(initialItem.id());
        assertThat(edited.items().getFirst().content()).isEqualTo("서류 발급 완료");
        assertThat(edited.items().getFirst().completed()).isFalse();
        assertThat(edited.items().getFirst().sortOrder()).isEqualTo(initialItem.sortOrder());
        DailyTaskDayResponse completed = tasks.updateMine(member.getId(), initialItem.id(),
                new DailyTaskUpdateRequest(DATE, 3L, null, true));
        assertThat(completed.items().getFirst().content()).isEqualTo("서류 발급 완료");
        assertThat(completed.items().getFirst().completed()).isTrue();
        DailyTaskDayResponse reopened = tasks.updateMine(member.getId(), initialItem.id(),
                new DailyTaskUpdateRequest(DATE, 4L, null, false));
        assertThat(reopened.items().getFirst().completed()).isFalse();

        DailyTaskDayResponse deleted = tasks.deleteMine(member.getId(), initialItem.id(), DATE, 5L);
        assertThat(deleted.revision()).isEqualTo(6L);
        assertThat(deleted.items()).containsExactly(untouched);
        assertThat(tasks.findMine(member.getId(), DATE.plusDays(1))).isEqualTo(tomorrow);
        assertThat(items.count()).isEqualTo(3); // Deleted create token survives as a tombstone.
        assertThat(days.count()).isEqualTo(2);
    }

    @Test
    void matchingCreateRetryUsesNormalizedOriginalPayloadBeforeCheckingOldRevision() {
        UUID requestId = UUID.randomUUID();
        var request = new DailyTaskCreateRequest(DATE, 0L, "  첫 요청  ", requestId);
        DailyTaskDayResponse created = tasks.createMine(member.getId(), request);
        Long id = created.items().getFirst().id();
        tasks.updateMine(member.getId(), id, new DailyTaskUpdateRequest(DATE, 1L, "수정된 최신 내용", true));

        DailyTaskDayResponse retry = tasks.createMine(member.getId(),
                new DailyTaskCreateRequest(DATE, 0L, "첫 요청", requestId));
        assertThat(retry.revision()).isEqualTo(2L);
        assertThat(retry.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.content()).isEqualTo("수정된 최신 내용");
            assertThat(item.completed()).isTrue();
        });
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void createRetryCannotResurrectDeletedTaskOrReuseTokenForChangedPayload() {
        UUID requestId = UUID.randomUUID();
        var request = new DailyTaskCreateRequest(DATE, 0L, "삭제할 원래 내용", requestId);
        Long id = tasks.createMine(member.getId(), request).items().getFirst().id();
        tasks.deleteMine(member.getId(), id, DATE, 1L);

        DailyTaskDayResponse retry = tasks.createMine(member.getId(), request);
        assertThat(retry.revision()).isEqualTo(2L);
        assertThat(retry.items()).isEmpty();
        assertStatus(409, () -> tasks.createMine(member.getId(),
                new DailyTaskCreateRequest(DATE, 2L, "다른 내용", requestId)));
        assertStatus(409, () -> tasks.createMine(member.getId(),
                new DailyTaskCreateRequest(DATE.plusDays(1), 0L, "삭제할 원래 내용", requestId)));
        assertStatus(404, () -> tasks.updateMine(member.getId(), id,
                new DailyTaskUpdateRequest(DATE, 2L, "복원 시도", null)));
        assertStatus(404, () -> tasks.deleteMine(member.getId(), id, DATE, 2L));
        assertThat(tasks.findMine(member.getId(), DATE).revision()).isEqualTo(2L);
        assertThat(tasks.findMine(member.getId(), DATE.plusDays(1)).revision()).isZero();
        assertThat(items.count()).isEqualTo(1);
        assertThat(days.count()).isEqualTo(1);
    }

    @Test
    void staleCreateUpdateAndDeleteCannotOverwriteNewerData() {
        Long id = create(member, DATE, 0L, "기존 내용").items().getFirst().id();
        tasks.updateMine(member.getId(), id, new DailyTaskUpdateRequest(DATE, 1L, "먼저 저장한 내용", true));

        assertStatus(409, () -> create(member, DATE, 1L, "오래된 화면의 추가"));
        assertStatus(409, () -> tasks.updateMine(member.getId(), id,
                new DailyTaskUpdateRequest(DATE, 1L, "오래된 화면의 수정", false)));
        assertStatus(409, () -> tasks.deleteMine(member.getId(), id, DATE, 1L));
        DailyTaskDayResponse current = tasks.findMine(member.getId(), DATE);
        assertThat(current.revision()).isEqualTo(2L);
        assertThat(current.items()).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("먼저 저장한 내용");
            assertThat(item.completed()).isTrue();
        });
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void ownMemberIsolationAppliesWithinAndAcrossBranchesAndToWrongDateTargets() {
        Member sameBranch = newMember(branch, MemberRole.MEMBER, "같은 지점 다른 회원", 13);
        Branch anotherBranch = branches.saveAndFlush(new Branch("다른 지점", "서울"));
        Member otherBranch = newMember(anotherBranch, MemberRole.MEMBER, "다른 지점 회원", 1);
        DailyTaskDayResponse own = create(member, DATE, 0L, "내 할 일");
        DailyTaskDayResponse same = create(sameBranch, DATE, 0L, "같은 지점 비공개 할 일");
        DailyTaskDayResponse other = create(otherBranch, DATE, 0L, "다른 지점 비공개 할 일");

        for (Long foreignId : List.of(same.items().getFirst().id(), other.items().getFirst().id())) {
            assertStatus(404, () -> tasks.updateMine(member.getId(), foreignId,
                    new DailyTaskUpdateRequest(DATE, 1L, "침범", true)));
            assertStatus(404, () -> tasks.deleteMine(member.getId(), foreignId, DATE, 1L));
        }
        Long ownId = own.items().getFirst().id();
        assertStatus(404, () -> tasks.updateMine(member.getId(), ownId,
                new DailyTaskUpdateRequest(DATE.plusDays(1), 0L, "날짜 이동 시도", null)));
        assertStatus(404, () -> tasks.deleteMine(member.getId(), ownId, DATE.plusDays(1), 0L));
        assertStatus(404, () -> tasks.deleteMine(member.getId(), Long.MAX_VALUE, DATE, 1L));
        assertThat(tasks.findMine(member.getId(), DATE)).isEqualTo(own);
        assertThat(tasks.findMine(sameBranch.getId(), DATE)).isEqualTo(same);
        assertThat(tasks.findMine(otherBranch.getId(), DATE)).isEqualTo(other);
        assertThat(tasks.findMine(member.getId(), DATE.plusDays(1)).revision()).isZero();
        assertThat(days.count()).isEqualTo(3);
    }

    @Test
    void createTokensAreScopedToMemberNotEntireDatabase() {
        UUID token = UUID.randomUUID();
        Member other = newMember(branch, MemberRole.MEMBER, "동일 요청 번호 다른 회원", 13);
        var request = new DailyTaskCreateRequest(DATE, 0L, "회원별 독립 요청", token);

        Long firstId = tasks.createMine(member.getId(), request).items().getFirst().id();
        Long secondId = tasks.createMine(other.getId(), request).items().getFirst().id();

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(items.count()).isEqualTo(2);
        assertThat(days.count()).isEqualTo(2);
    }

    @Test
    void staffAndAdminCannotReadOrWritePersonalTasks() {
        for (MemberRole role : List.of(MemberRole.STAFF, MemberRole.ADMIN)) {
            Member operator = newMember(branch, role, "권한 확인 " + role, null);
            assertStatus(403, () -> tasks.findMine(operator.getId(), DATE));
            assertStatus(403, () -> tasks.findCalendarMine(operator.getId(), "2026-10"));
            assertStatus(403, () -> create(operator, DATE, 0L, "관리자 개인 기록 시도"));
            assertStatus(403, () -> tasks.updateMine(operator.getId(), 1L,
                    new DailyTaskUpdateRequest(DATE, 0L, "수정", null)));
            assertStatus(403, () -> tasks.deleteMine(operator.getId(), 1L, DATE, 0L));
        }
        assertThat(items.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void invalidCreatePayloadsDoNotWriteOrAdvanceState() {
        for (Long revision : new Long[] {null, -1L, MAX_SAFE_REVISION + 1}) {
            assertStatus(400, () -> create(member, DATE, revision, "버전 검증"));
        }
        for (String content : new String[] {null, "", "  \n\t  ", "가".repeat(2001)}) {
            assertStatus(400, () -> create(member, DATE, 0L, content));
        }
        for (LocalDate date : new LocalDate[] {null, LocalDate.of(0, 1, 1), LocalDate.of(10000, 1, 1)}) {
            assertStatus(400, () -> create(member, date, 0L, "날짜 검증"));
        }
        assertStatus(400, () -> tasks.createMine(member.getId(),
                new DailyTaskCreateRequest(DATE, 0L, "요청 번호 없음", null)));
        assertThat(items.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void invalidUpdatesAndDeletesPreserveCurrentTaskAndRevision() {
        DailyTaskDayResponse original = create(member, DATE, 0L, "보존할 내용");
        Long id = original.items().getFirst().id();
        for (String content : new String[] {"", " \n ", "a".repeat(2001)}) {
            assertStatus(400, () -> tasks.updateMine(member.getId(), id,
                    new DailyTaskUpdateRequest(DATE, 1L, content, true)));
        }
        assertStatus(400, () -> tasks.updateMine(member.getId(), id,
                new DailyTaskUpdateRequest(DATE, 1L, null, null)));
        assertStatus(400, () -> tasks.updateMine(member.getId(), id,
                new DailyTaskUpdateRequest(null, 1L, "날짜 없음", null)));
        for (Long revision : new Long[] {null, -1L, MAX_SAFE_REVISION + 1}) {
            assertStatus(400, () -> tasks.updateMine(member.getId(), id,
                    new DailyTaskUpdateRequest(DATE, revision, "잘못된 버전", null)));
            assertStatus(400, () -> tasks.deleteMine(member.getId(), id, DATE, revision));
        }
        assertStatus(400, () -> tasks.deleteMine(member.getId(), id, null, 1L));
        assertThat(tasks.findMine(member.getId(), DATE)).isEqualTo(original);
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void maximumContentAndSupportedBoundaryDatesAreAccepted() {
        String content = "가".repeat(2000);
        DailyTaskDayResponse first = create(member, LocalDate.of(1, 1, 1), 0L, content);
        DailyTaskDayResponse last = create(member, LocalDate.of(9999, 12, 31), 0L, "마지막 지원 날짜");

        assertThat(first.items().getFirst().content()).isEqualTo(content);
        assertThat(last.date()).isEqualTo(LocalDate.of(9999, 12, 31));
        assertThat(tasks.findCalendarMine(member.getId(), "0001-01").days()).hasSize(31);
        assertThat(tasks.findCalendarMine(member.getId(), "9999-12").days()).hasSize(31);
    }

    @Test
    void calendarCountsOnlyOwnLiveTasksAndHandlesEmptyDaysMonthBoundariesAndLeapYears() {
        LocalDate firstDay = LocalDate.of(2026, 10, 1);
        LocalDate lastDay = LocalDate.of(2026, 10, 31);
        Long firstId = create(member, firstDay, 0L, "완료할 할 일").items().getFirst().id();
        tasks.updateMine(member.getId(), firstId, new DailyTaskUpdateRequest(firstDay, 1L, null, true));
        create(member, firstDay, 2L, "미완료 할 일");
        Long deletedId = create(member, lastDay, 0L, "삭제할 할 일").items().getFirst().id();
        tasks.deleteMine(member.getId(), deletedId, lastDay, 1L);
        create(member, firstDay.minusDays(1), 0L, "지난달");
        create(member, lastDay.plusDays(1), 0L, "다음달");
        Member other = newMember(branch, MemberRole.MEMBER, "다른 회원 달력", 13);
        create(other, firstDay, 0L, "타인 기록");

        var calendar = tasks.findCalendarMine(member.getId(), "2026-10");
        assertThat(calendar.days()).hasSize(31);
        assertThat(calendar.days()).extracting(DailyTaskCalendarDayResponse::date).isSorted().doesNotHaveDuplicates();
        assertThat(calendar.days().getFirst().totalCount()).isEqualTo(2);
        assertThat(calendar.days().getFirst().completedCount()).isEqualTo(1);
        assertThat(calendar.days().subList(1, 31)).allSatisfy(day -> {
            assertThat(day.totalCount()).isZero();
            assertThat(day.completedCount()).isZero();
        });
        assertThat(tasks.findCalendarMine(member.getId(), "2024-02").days()).hasSize(29);
        assertThat(tasks.findCalendarMine(member.getId(), "2026-02").days()).hasSize(28);
        assertThat(tasks.findMine(member.getId(), lastDay).revision()).isEqualTo(2L);
    }

    @Test
    void invalidCalendarAndReadDatesDoNotCreateRecords() {
        for (String month : new String[] {null, "", "2026-1", "2026-00", "2026-13", "0000-01", "10000-01", "2026-10-01"}) {
            assertStatus(400, () -> tasks.findCalendarMine(member.getId(), month));
        }
        assertStatus(400, () -> tasks.findMine(member.getId(), null));
        assertStatus(400, () -> tasks.findMine(member.getId(), LocalDate.of(0, 1, 1)));
        assertStatus(400, () -> tasks.findMine(member.getId(), LocalDate.of(10000, 1, 1)));
        assertThat(items.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void simultaneousFirstWritesHaveOneWinnerAndOneConflict() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> write = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                create(member, DATE, 0L, "동시 추가 " + Thread.currentThread().threadId());
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
        assertThat(items.count()).isEqualTo(1);
        assertThat(days.count()).isEqualTo(1);
        assertThat(tasks.findMine(member.getId(), DATE).revision()).isEqualTo(1L);
    }

    @Test
    void simultaneousDuplicateCreatesReturnTheSameItemWithoutDuplicateRows() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        var request = new DailyTaskCreateRequest(DATE, 0L, "같은 요청 재전송", UUID.randomUUID());
        Callable<DailyTaskDayResponse> write = () -> {
            start.await(5, TimeUnit.SECONDS);
            return tasks.createMine(member.getId(), request);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(write);
            var second = executor.submit(write);
            DailyTaskDayResponse firstResult = first.get(15, TimeUnit.SECONDS);
            DailyTaskDayResponse secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(firstResult).isEqualTo(secondResult);
            assertThat(firstResult.revision()).isEqualTo(1L);
        }
        assertThat(items.count()).isEqualTo(1);
        assertThat(days.count()).isEqualTo(1);
    }

    @Test
    void simultaneousUpdateAndDeleteFromOneRevisionCannotBothSucceed() throws Exception {
        Long id = create(member, DATE, 0L, "처음 읽은 기록").items().getFirst().id();
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> update = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                tasks.updateMine(member.getId(), id, new DailyTaskUpdateRequest(DATE, 1L, "수정된 기록", true));
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        Callable<Integer> remove = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                tasks.deleteMine(member.getId(), id, DATE, 1L);
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };

        int updateStatus;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(update);
            var second = executor.submit(remove);
            updateStatus = first.get(15, TimeUnit.SECONDS);
            assertThat(List.of(updateStatus, second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        DailyTaskDayResponse current = tasks.findMine(member.getId(), DATE);
        assertThat(current.revision()).isEqualTo(2L);
        if (updateStatus == 200) {
            assertThat(current.items()).singleElement().satisfies(item -> {
                assertThat(item.content()).isEqualTo("수정된 기록");
                assertThat(item.completed()).isTrue();
            });
        } else {
            assertThat(current.items()).isEmpty();
        }
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void personalTasksDoNotChangeWeeklyPlansStaffTodosOrStudyAndBreakTime() {
        WeeklyPlanItem weekly = weeklyItems.saveAndFlush(new WeeklyPlanItem(member.getId(), branch.getId(), DATE,
                0, 0, "기존 교시별 계획", true, 4));
        WeeklyPlanGoal goal = weeklyGoals.saveAndFlush(new WeeklyPlanGoal(member.getId(), branch.getId(), DATE, "주간 목표"));
        TodoItem staffTodo = staffTodos.saveAndFlush(new TodoItem(branch.getId(), DATE, "운영용 할 일",
                TodoPriority.NORMAL, TodoSourceType.MANUAL, null, member.getId(), member.getId(), member.getName()));
        StudyPresenceSession presence = StudyPresenceSession.qrCheckIn(member.getId(), branch.getId(),
                Instant.parse("2026-10-26T00:00:00Z"));
        presence.checkOut(Instant.parse("2026-10-26T02:00:00Z"));
        presence = presences.saveAndFlush(presence);
        StudyBreakSession breakSession = new StudyBreakSession(presence.getId(), member.getId(), branch.getId(), DATE,
                StudyBreak.AFTER_FIRST, Instant.parse("2026-10-26T01:30:00Z"),
                Instant.parse("2026-10-26T01:45:00Z"), Instant.parse("2026-10-26T01:31:00Z"));
        breakSession.stopByMember(Instant.parse("2026-10-26T01:40:00Z"));
        breakSession = breaks.saveAndFlush(breakSession);
        var before = studyReports.findMine(member.getId(), DATE, DATE);
        assertThat(before.totals().recognizedPeriodDuration().totalSeconds()).isEqualTo(105 * 60);
        assertThat(before.totals().recognizedBreakDuration().totalSeconds()).isEqualTo(9 * 60);

        Long id = create(member, DATE, 0L, "새 개인 할 일").items().getFirst().id();
        tasks.updateMine(member.getId(), id, new DailyTaskUpdateRequest(DATE, 1L, "수정 및 완료", true));
        tasks.deleteMine(member.getId(), id, DATE, 2L);

        WeeklyPlanItem preserved = weeklyItems.findById(weekly.getId()).orElseThrow();
        assertThat(preserved.getContent()).isEqualTo(weekly.getContent());
        assertThat(preserved.isDone()).isTrue();
        assertThat(preserved.getSortOrder()).isEqualTo(4);
        assertThat(preserved.getUpdatedAt()).isEqualTo(weekly.getUpdatedAt());
        assertThat(weeklyGoals.findById(goal.getId()).orElseThrow().getGoal()).isEqualTo("주간 목표");
        TodoItem preservedTodo = staffTodos.findById(staffTodo.getId()).orElseThrow();
        assertThat(preservedTodo.getContent()).isEqualTo("운영용 할 일");
        assertThat(preservedTodo.isCompleted()).isFalse();
        assertThat(preservedTodo.getUpdatedAt()).isEqualTo(staffTodo.getUpdatedAt());
        assertThat(studyReports.findMine(member.getId(), DATE, DATE)).isEqualTo(before);
        assertThat(presences.findById(presence.getId())).isPresent();
        assertThat(breaks.findById(breakSession.getId()).orElseThrow().getEndedAt())
                .isEqualTo(Instant.parse("2026-10-26T01:40:00Z"));
    }

    @Test
    void everyHttpRouteRequiresAuthentication() throws Exception {
        mvc.perform(get(BASE).param("date", DATE.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/calendar").param("month", "2026-10")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE + "/items/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/items/1").param("date", DATE.toString()).param("expectedRevision", "0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void httpCrudUsesJwtOwnerAndDoesNotExposeInternalReceiptFields() throws Exception {
        Member other = newMember(branch, MemberRole.MEMBER, "본문 속 다른 회원", 13);
        String body = """
                {"date":"2026-10-26","expectedRevision":0,"content":"  HTTP 개인 할 일  ",
                 "clientRequestId":"%s","memberId":%d,"branchId":999999}
                """.formatted(UUID.randomUUID(), other.getId());
        mvc.perform(post(BASE + "/items").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.date").value(DATE.toString()))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.items[0].content").value("HTTP 개인 할 일"))
                .andExpect(jsonPath("$.items[0].completed").value(false))
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.items[0].clientRequestId").doesNotExist())
                .andExpect(jsonPath("$.items[0].deleted").doesNotExist());
        Long id = tasks.findMine(member.getId(), DATE).items().getFirst().id();
        mvc.perform(patch(BASE + "/items/{id}", id).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-26","expectedRevision":1,"completed":true}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.items[0].completed").value(true));
        mvc.perform(get(BASE + "/calendar").header("Authorization", bearer(member)).param("month", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[25].totalCount").value(1))
                .andExpect(jsonPath("$.days[25].completedCount").value(1));
        mvc.perform(delete(BASE + "/items/{id}", id).header("Authorization", bearer(member))
                        .param("date", DATE.toString()).param("expectedRevision", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(tasks.findMine(other.getId(), DATE).items()).isEmpty();
    }

    @Test
    void httpRejectsStaleWritesAndMalformedRequestsWithoutChangingData() throws Exception {
        Long id = create(member, DATE, 0L, "보존할 HTTP 기록").items().getFirst().id();
        mvc.perform(patch(BASE + "/items/{id}", id).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-10-26","expectedRevision":0,"completed":true}
                                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post(BASE + "/items").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization", bearer(member)).param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/calendar").header("Authorization", bearer(member)).param("month", "2026-13"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/items/{id}", id).header("Authorization", bearer(member))
                        .param("date", DATE.toString()))
                .andExpect(status().isBadRequest());
        assertThat(tasks.findMine(member.getId(), DATE).revision()).isEqualTo(1L);
        assertThat(tasks.findMine(member.getId(), DATE).items().getFirst().completed()).isFalse();
    }

    @Test
    void currentDatabaseRoleOverridesAnOldMemberToken() throws Exception {
        String oldMemberToken = bearer(member);
        member.update(branch.getId(), member.getName(), MemberRole.STAFF, member.getSeatNumber(),
                member.getJoinDate(), member.getCertificationId(), null);
        members.saveAndFlush(member);

        mvc.perform(get(BASE).header("Authorization", oldMemberToken).param("date", DATE.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/calendar").header("Authorization", oldMemberToken).param("month", "2026-10"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/items").header("Authorization", oldMemberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"date":"2026-10-26","expectedRevision":0,"content":"예전 권한 시도",
                                 "clientRequestId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        assertThat(items.count()).isZero();
        assertThat(days.count()).isZero();
    }

    private DailyTaskDayResponse create(Member owner, LocalDate date, Long revision, String content) {
        return tasks.createMine(owner.getId(), new DailyTaskCreateRequest(date, revision, content, UUID.randomUUID()));
    }

    private Member newMember(Branch owningBranch, MemberRole role, String name, Integer seat) {
        return members.saveAndFlush(new Member(owningBranch.getId(), name, "test-password", role,
                seat, DATE.minusMonths(1), 3L));
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
        Clock dailyTaskTestClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
