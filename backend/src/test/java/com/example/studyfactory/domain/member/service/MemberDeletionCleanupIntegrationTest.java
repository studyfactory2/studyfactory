package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.common.config.TimeConfig;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceDailyInitialization;
import com.example.studyfactory.domain.attendance.entity.AttendanceNote;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceReviewedAbsence;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceUnconfirmedSlot;
import com.example.studyfactory.domain.auth.domain.RefreshToken;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.entity.BeveragePreferenceAudit;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.beverage.service.LegacyBeverageCleanupService;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.dailyTask.entity.DailyTaskDay;
import com.example.studyfactory.domain.dailyTask.entity.DailyTaskItem;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveOccurrenceCancellation;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.room.entity.Seat;
import com.example.studyfactory.domain.room.service.SeatService;
import com.example.studyfactory.domain.sideDish.entity.MealType;
import com.example.studyfactory.domain.sideDish.entity.SideDishMealInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishOrderInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishReferenceInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import com.example.studyfactory.domain.staffSchedule.entity.StaffScheduleShift;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoPriority;
import com.example.studyfactory.domain.todo.entity.TodoReply;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import com.example.studyfactory.domain.weeklyPlan.entity.BreakPlanCreateReceipt;
import com.example.studyfactory.domain.weeklyPlan.entity.MonthlyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanItem;
import com.example.studyfactory.domain.weeklyPlan.entity.SharedPlanWeek;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanGoal;
import com.example.studyfactory.domain.weeklyPlan.entity.WeeklyPlanItem;
import com.example.studyfactory.domain.weeklyPlan.model.PlanGroup;
import com.example.studyfactory.domain.weeklyPlan.service.WeeklyPlanService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Use the pooled test datasource: H2 IN-check constraints retain their creating
// session, which the default unpooled embedded datasource closes between calls.
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:member-cleanup-shared-plans;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemberService.class, MemberDeletionCleanupService.class, BeverageService.class,
        LegacyBeverageCleanupService.class, WeeklyPlanService.class, SeatService.class,
        RegistrationCodeService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("회원 영구 삭제 실제 저장소 통합 테스트")
class MemberDeletionCleanupIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 15);
    private static final Instant NOW = Instant.parse("2026-10-15T03:10:00Z");
    private static final List<String> MEMBER_TABLES = List.of(
            "attendances", "attendance_reviewed_absences", "attendance_unconfirmed_slots",
            "attendance_daily_initializations", "beverage_items", "beverage_preference_audits",
            "leave_requests", "special_leaves", "fixed_leaves", "leave_occurrence_cancellations",
            "weekly_plan_items", "weekly_plan_goals", "monthly_plan_goals",
            "shared_plan_items", "shared_plan_weeks", "break_plan_create_receipts", "suggestions",
            "member_task_days", "member_task_items",
            "side_dish_requests", "study_break_sessions", "study_presence_sessions", "refresh_tokens"
    );

    @Autowired
    private MemberService memberService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        // This production migration FK is not expressed as a JPA relationship.
        jdbcTemplate.execute("""
                alter table study_break_sessions add constraint fk_cleanup_presence
                foreign key (presence_session_id) references study_presence_sessions (id)
                """);
        fixture = transaction.execute(status -> seed());
    }

    @Test
    @DisplayName("계정과 모든 개인 기록은 삭제하고 다른 회원 및 지점 공유 기록은 보존한다")
    void permanentlyDeletesOwnedRecordsWithoutRemovingOtherMembersWork() {
        memberService.delete(fixture.adminId(), fixture.deletedId());

        assertThat(count("members", "id", fixture.deletedId())).isZero();
        for (String table : MEMBER_TABLES) {
            assertThat(count(table, "member_id", fixture.deletedId())).as(table).isZero();
            assertThat(count(table, "member_id", fixture.otherId())).as("other member: " + table).isPositive();
        }
        assertSharedPlansRetained(fixture.otherId());
        assertDailyTasksRetained(fixture.otherId());
        assertThat(count("todo_items", "target_member_id", fixture.deletedId())).isZero();
        assertThat(count("todo_replies", "todo_item_id", fixture.ownedTodoId())).isZero();
        assertThat(count("todo_replies", "member_id", fixture.deletedId())).isZero();
        assertThat(count("todo_replies", "todo_item_id", fixture.otherTodoId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select member_id from seats where id = ?", Long.class,
                fixture.seatId())).isNull();

        assertThat(count("members", "id", fixture.otherId())).isEqualTo(1);
        assertThat(count("staff_schedules", "branch_id", fixture.branchId())).isEqualTo(1);
        assertThat(count("attendance_notes", "created_by_member_id", fixture.deletedId())).isEqualTo(1);
        assertThat(count("attendance_daily_initializations", "initialized_by_member_id", fixture.deletedId()))
                .isEqualTo(1);
        assertThat(count("special_leaves", "created_by_member_id", fixture.deletedId())).isEqualTo(1);
        assertThat(count("leave_occurrence_cancellations", "created_by_member_id", fixture.deletedId())).isEqualTo(1);
        assertThat(count("study_presence_sessions", "checked_in_by_member_id", fixture.deletedId())).isEqualTo(1);

        assertThat(count("attendances", "marked_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("attendance_reviewed_absences", "reviewed_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("attendance_unconfirmed_slots", "cleared_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("suggestions", "resolved_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("study_presence_sessions", "closed_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("todo_items", "created_by_member_id", fixture.deletedId())).isZero();
        assertThat(count("todo_items", "completed_by_member_id", fixture.deletedId())).isZero();
        assertThat(jdbcTemplate.queryForObject("select created_by_member_name from todo_items where id = ?",
                String.class, fixture.otherTodoId())).isNull();
        assertThat(jdbcTemplate.queryForObject("select completed from todo_items where id = ?",
                Boolean.class, fixture.otherTodoId())).isTrue();
        assertThat(jdbcTemplate.queryForObject("select is_resolved from suggestions where member_id = ?",
                Boolean.class, fixture.otherId())).isTrue();
    }

    @Test
    @DisplayName("삭제 트랜잭션 실패 시 계정, 개인 기록, 좌석, 다른 회원 감사 정보 모두 복원된다")
    void failureRollsBackTheEntireDeletion() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            memberService.delete(fixture.adminId(), fixture.deletedId());
            entityManager.flush();
            throw new IllegalStateException("simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("members", "id", fixture.deletedId())).isEqualTo(1);
        for (String table : MEMBER_TABLES) {
            assertThat(count(table, "member_id", fixture.deletedId())).as(table).isPositive();
        }
        assertSharedPlansRetained(fixture.deletedId());
        assertSharedPlansRetained(fixture.otherId());
        assertDailyTasksRetained(fixture.deletedId());
        assertDailyTasksRetained(fixture.otherId());
        assertThat(count("todo_replies", "todo_item_id", fixture.ownedTodoId())).isEqualTo(1);
        assertThat(count("todo_replies", "member_id", fixture.deletedId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select member_id from seats where id = ?", Long.class,
                fixture.seatId())).isEqualTo(fixture.deletedId());
        assertThat(jdbcTemplate.queryForObject("select created_by_member_id from todo_items where id = ?",
                Long.class, fixture.otherTodoId())).isEqualTo(fixture.deletedId());
        assertThat(jdbcTemplate.queryForObject("select marked_by_member_id from attendances where member_id = ?",
                Long.class, fixture.otherId())).isEqualTo(fixture.deletedId());
    }

    private Fixture seed() {
        Branch branch = persist(new Branch("삭제 테스트 지점"));
        Member admin = persist(member(branch.getId(), "관리자", MemberRole.ADMIN, null));
        Member deleted = persist(member(branch.getId(), "삭제할 스태프", MemberRole.STAFF, 12));
        deleted.issueRegistrationCode("code-hash", LocalDateTime.of(2026, 10, 16, 9, 0));
        Member other = persist(member(branch.getId(), "다른 회원", MemberRole.MEMBER, null));
        long ownTodoId = seedOwnedRecords(deleted, deleted.getId());
        long otherTodoId = seedOwnedRecords(other, deleted.getId());
        persist(new TodoReply(ownTodoId, other.getId(), other.getName(), "삭제되는 할 일의 답글"));
        persist(new TodoReply(otherTodoId, deleted.getId(), deleted.getName(), "삭제할 작성자의 답글"));
        persist(new TodoReply(otherTodoId, other.getId(), other.getName(), "보존할 답글"));
        persist(new AttendanceNote(branch.getId(), DATE, "지점 공용 안내", deleted.getId()));
        persist(new StaffSchedule(branch.getId(), DATE.getDayOfWeek(), StaffScheduleShift.MORNING,
                "음료", deleted.getName()));
        Seat seat = persist(new Seat(branch.getId(), 1L, 12, deleted.getId(), 1, 1));
        return new Fixture(admin.getId(), deleted.getId(), other.getId(), branch.getId(),
                ownTodoId, otherTodoId, seat.getId());
    }

    private long seedOwnedRecords(Member member, long actorId) {
        long memberId = member.getId();
        long branchId = member.getBranchId();
        persist(new Attendance(new AttendanceReferenceInformation(memberId, branchId, 1L, actorId),
                new AttendanceSlotInformation(DATE, 1, null)));
        persist(new AttendanceReviewedAbsence(memberId, branchId, DATE, 2, actorId, NOW));
        persist(new AttendanceUnconfirmedSlot(memberId, branchId, DATE, 3, actorId, NOW));
        persist(new AttendanceDailyInitialization(memberId, branchId, DATE, actorId));
        persist(new BeverageItem(memberId, "아메리카노", ""));
        persist(new BeveragePreferenceAudit(memberId, NOW, NOW));
        persist(new LeaveRequest(memberId, branchId, DATE, LeaveType.AFTERNOON));
        FixedLeave fixed = persist(new FixedLeave(memberId, branchId, DATE.getDayOfWeek(), "4,5", "알바", true));
        persist(new SpecialLeave(memberId, branchId, DATE, "4,5", "알바", null, true, actorId, fixed.getId()));
        persist(new LeaveOccurrenceCancellation(memberId, branchId, LeaveSourceType.FIXED_LEAVE,
                fixed.getId(), DATE, 4, actorId));
        persist(new WeeklyPlanGoal(memberId, branchId, DATE, "주간 목표"));
        persist(new WeeklyPlanItem(memberId, branchId, DATE, 1, 1, "계획", false, 0));
        WeeklyPlanItem lunchPlan = persist(new WeeklyPlanItem(memberId, branchId, DATE, 100, 0,
                "점심 계획", false, 0));
        persist(new BreakPlanCreateReceipt(memberId, DATE, new UUID(memberId, 3L), "c".repeat(64),
                lunchPlan.getId()));
        WeeklyPlanItem deletedDinnerPlan = persist(new WeeklyPlanItem(memberId, branchId, DATE, 101, 0,
                "삭제한 저녁 계획", false, 0));
        persist(new BreakPlanCreateReceipt(memberId, DATE, new UUID(memberId, 4L), "d".repeat(64),
                deletedDinnerPlan.getId()));
        entityManager.remove(deletedDinnerPlan);
        persist(new MonthlyPlanGoal(memberId, branchId, YearMonth.from(DATE), "월간 목표"));
        SharedPlanWeek sharedWeek = new SharedPlanWeek(memberId, DATE);
        sharedWeek.advance(true);
        persist(sharedWeek);
        persist(new SharedPlanItem(memberId, branchId, DATE, 0, PlanGroup.PERIODS_1_2,
                "보존할 공유 계획", 0, new UUID(memberId, 1L), "a".repeat(64)));
        SharedPlanItem deletedSharedItem = new SharedPlanItem(memberId, branchId, DATE, 0,
                PlanGroup.PERIODS_3_5, "삭제된 계획의 재시도 토큰", 0,
                new UUID(memberId, 2L), "b".repeat(64));
        deletedSharedItem.markDeleted();
        persist(deletedSharedItem);
        DailyTaskDay taskDay = new DailyTaskDay(memberId, DATE);
        taskDay.advance();
        persist(taskDay);
        persist(new DailyTaskItem(memberId, DATE, "보존할 개인 할 일", 0,
                new UUID(memberId, 5L), "e".repeat(64)));
        DailyTaskItem deletedTask = new DailyTaskItem(memberId, DATE, "삭제된 개인 할 일의 재시도 토큰", 1,
                new UUID(memberId, 6L), "f".repeat(64));
        deletedTask.markDeleted();
        persist(deletedTask);
        Suggestion suggestion = new Suggestion(new SuggestionReferenceInformation(memberId, branchId, null),
                SuggestionCategory.GENERAL, "건의", false);
        suggestion.resolve(actorId);
        persist(suggestion);
        persist(new SideDishRequest(new SideDishReferenceInformation(memberId, branchId),
                new SideDishMealInformation(DATE, MealType.LUNCH), new SideDishOrderInformation("반찬", 5000)));
        persist(new RefreshToken(memberId, "test-token-" + memberId));

        StudyPresenceSession closed = StudyPresenceSession.managerCheckIn(memberId, branchId,
                NOW.minusSeconds(7200), actorId, "수동 입실");
        closed.managerCheckOut(NOW.minusSeconds(3600), actorId);
        persist(closed);
        StudyBreakSession closedBreak = breakSession(closed, memberId, branchId, NOW.minusSeconds(7000));
        closedBreak.stopByMember(NOW.minusSeconds(6900));
        persist(closedBreak);
        StudyPresenceSession active = persist(StudyPresenceSession.qrCheckIn(memberId, branchId, NOW));
        persist(breakSession(active, memberId, branchId, NOW));

        TodoItem todo = new TodoItem(branchId, DATE, "회원 할 일", TodoPriority.NORMAL,
                TodoSourceType.SUGGESTION, suggestion.getId(), memberId, actorId, "삭제할 스태프");
        todo.updateCompletion(true, actorId);
        return persist(todo).getId();
    }

    private StudyBreakSession breakSession(StudyPresenceSession presence, long memberId, long branchId, Instant start) {
        return new StudyBreakSession(presence.getId(), memberId, branchId, DATE, StudyBreak.LUNCH,
                start, start.plusSeconds(600), start);
    }

    private Member member(long branchId, String name, MemberRole role, Integer seat) {
        return new Member(branchId, name, "test-password", role, seat, DATE, null);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private long count(String table, String column, long value) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + column + " = ?",
                Long.class, value);
    }

    private void assertSharedPlansRetained(long memberId) {
        assertThat(count("shared_plan_weeks", "member_id", memberId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select revision from shared_plan_weeks where member_id = ?", Long.class, memberId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select legacy_save_protected from shared_plan_weeks where member_id = ?",
                Boolean.class, memberId)).isTrue();
        assertThat(count("shared_plan_items", "member_id", memberId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select content from shared_plan_items where member_id = ? and deleted = false",
                String.class, memberId)).isEqualTo("보존할 공유 계획");
        assertThat(jdbcTemplate.queryForObject(
                "select content from shared_plan_items where member_id = ? and deleted = true",
                String.class, memberId)).isEqualTo("삭제된 계획의 재시도 토큰");
        assertThat(jdbcTemplate.queryForList(
                "select client_request_id from shared_plan_items where member_id = ?", UUID.class, memberId))
                .containsExactlyInAnyOrder(new UUID(memberId, 1L), new UUID(memberId, 2L));
        assertThat(count("break_plan_create_receipts", "member_id", memberId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "select client_request_id from break_plan_create_receipts where member_id = ?",
                UUID.class, memberId)).containsExactlyInAnyOrder(new UUID(memberId, 3L), new UUID(memberId, 4L));
        assertThat(jdbcTemplate.queryForList(
                "select create_fingerprint from break_plan_create_receipts where member_id = ?",
                String.class, memberId)).containsExactlyInAnyOrder("c".repeat(64), "d".repeat(64));
        Long lunchItemId = jdbcTemplate.queryForObject(
                "select item_id from break_plan_create_receipts where member_id = ? and client_request_id = ?",
                Long.class, memberId, new UUID(memberId, 3L));
        Long deletedDinnerItemId = jdbcTemplate.queryForObject(
                "select item_id from break_plan_create_receipts where member_id = ? and client_request_id = ?",
                Long.class, memberId, new UUID(memberId, 4L));
        assertThat(count("weekly_plan_items", "id", lunchItemId)).isEqualTo(1);
        assertThat(count("weekly_plan_items", "id", deletedDinnerItemId)).isZero();
    }

    private void assertDailyTasksRetained(long memberId) {
        assertThat(count("member_task_days", "member_id", memberId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select revision from member_task_days where member_id = ?", Long.class, memberId))
                .isEqualTo(1L);
        assertThat(count("member_task_items", "member_id", memberId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select content from member_task_items where member_id = ? and deleted = false",
                String.class, memberId)).isEqualTo("보존할 개인 할 일");
        assertThat(jdbcTemplate.queryForObject(
                "select content from member_task_items where member_id = ? and deleted = true",
                String.class, memberId)).isEqualTo("삭제된 개인 할 일의 재시도 토큰");
        assertThat(jdbcTemplate.queryForList(
                "select client_request_id from member_task_items where member_id = ?", UUID.class, memberId))
                .containsExactlyInAnyOrder(new UUID(memberId, 5L), new UUID(memberId, 6L));
    }

    private record Fixture(long adminId, long deletedId, long otherId, long branchId,
                           long ownedTodoId, long otherTodoId, long seatId) {
    }
}
