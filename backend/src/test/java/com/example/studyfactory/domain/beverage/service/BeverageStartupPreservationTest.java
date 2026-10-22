package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.StudyfactoryApplication;
import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import com.example.studyfactory.domain.attendance.repository.AttendanceRepository;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import com.example.studyfactory.domain.beverage.dto.BeverageItemRequest;
import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.leave.entity.LeaveRequest;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import com.example.studyfactory.domain.leave.repository.FixedLeaveRepository;
import com.example.studyfactory.domain.leave.repository.LeaveRequestRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.sql.DriverManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

@DisplayName("음료 설정 재시작 보존 테스트")
class BeverageStartupPreservationTest {

    @Test
    @DisplayName("구버전 데이터가 남아 있어도 수정한 음료와 삭제 상태를 두 번의 실제 재시작 후 유지한다")
    void repeatedApplicationStartsPreserveCurrentPreferencesAndLegacyEvidence() throws Exception {
        // A unique in-memory database survives context shutdown, but cannot reach a developer/production database.
        String jdbcUrl = "jdbc:h2:mem:beverage-restart-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Map<String, List<Map<String, Object>>> expectedRows;
        Map<Long, BeveragePreferenceResponse> expectedResponses;

        try {
            try (ConfigurableApplicationContext context = startApplication(jdbcUrl)) {
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                createLegacyTables(jdbc);
                List<Long> memberIds = prepareCorrectedPreferences(context, jdbc);
                expectedRows = snapshot(jdbc);
                expectedResponses = readPreferences(context, memberIds);
            }

            for (int restart = 1; restart <= 2; restart++) {
                try (ConfigurableApplicationContext context = startApplication(jdbcUrl)) {
                    Map<String, List<Map<String, Object>>> actual = snapshot(context.getBean(JdbcTemplate.class));
                    for (String table : expectedRows.keySet()) {
                        assertThat(actual.get(table))
                                .as("%s must not change during application restart %s", table, restart)
                                .isEqualTo(expectedRows.get(table));
                    }
                    assertThat(readPreferences(context, List.copyOf(expectedResponses.keySet())))
                            .as("API item lists and lifecycle timestamps must survive restart %s", restart)
                            .isEqualTo(expectedResponses);
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                 var statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }

    private ConfigurableApplicationContext startApplication(String jdbcUrl) {
        SpringApplication application = new SpringApplication(StudyfactoryApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            // Keep real startup runners; disable unrelated timed jobs so they cannot race the snapshots.
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            String scheduler = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
            if (registry.containsBeanDefinition(scheduler)) {
                registry.removeBeanDefinition(scheduler);
            }
        }));
        return application.run(
                "--spring.profiles.active=beverage-startup-preservation-test",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.hikari.maximum-pool-size=2",
                "--spring.jpa.hibernate.ddl-auto=update",
                "--spring.jpa.open-in-view=false",
                "--spring.docker.compose.enabled=false",
                "--spring.main.banner-mode=off",
                "--study-break.reconciliation.enabled=false",
                "--study-presence.auto-close.enabled=false",
                "--study-presence.qr.secret=c3R1ZHlmYWN0b3J5LXRlc3QtcXItc2VjcmV0LWtleS0yMDI2LXRlc3Rz",
                "--jwt.secret=studyfactory-test-secret-key-for-jwt-token-generation",
                "--logging.level.root=WARN"
        );
    }

    private List<Long> prepareCorrectedPreferences(ConfigurableApplicationContext context, JdbcTemplate jdbc) {
        Branch branch = context.getBean(BranchRepository.class).save(new Branch("음료 재시작 검증 지점"));
        MemberRepository members = context.getBean(MemberRepository.class);
        BeverageService beverages = context.getBean(BeverageService.class);

        Long deletedOne = member(members, branch, "한 항목 삭제");
        archive(jdbc, deletedOne, "아아", "삭제 전 메모");
        beverages.updateDrink(deletedOne, request(item("아아", "삭제 전 메모"), item("물", "현재 메모")));
        beverages.deleteDrinkItem(deletedOne, "아아");
        assertThat(beverages.findMyDrink(deletedOne).items()).extracting("name").containsExactly("물");

        Long replaced = member(members, branch, "반컵으로 변경");
        archive(jdbc, replaced, "선식", "예전 한 컵");
        beverages.updateDrink(replaced, request(item("선식", "예전 한 컵")));
        beverages.updateDrink(replaced, request(item("선식반컵", "현재 반 컵")));
        assertThat(beverages.findMyDrink(replaced).items()).extracting("name").containsExactly("선식반컵");

        Long deletedAll = member(members, branch, "모두 삭제");
        archive(jdbc, deletedAll, "아아", "그만 마시는 음료");
        beverages.updateDrink(deletedAll, request(item("아아", "그만 마시는 음료"), item("해독", null)));
        beverages.deleteDrink(deletedAll);
        assertThat(beverages.findMyDrink(deletedAll).items()).isEmpty();
        assertThat(beverages.findMyDrink(deletedAll).updatedAt()).isNotNull();

        Long unchanged = member(members, branch, "기존 설정 유지");
        archive(jdbc, unchanged, "차", "옛날 메모");
        beverages.updateDrink(unchanged, request(item("차", "수정한 메모")));

        Long duplicates = member(members, branch, "같은 음료 두 잔");
        archive(jdbc, duplicates, "선식", "옛날 한 잔");
        beverages.updateDrink(duplicates, request(item("선식", "첫 번째 잔"), item("선식", "두 번째 잔")));
        assertThat(beverages.findMyDrink(duplicates).items()).extracting("name").containsExactly("선식", "선식");

        Long legacyOnly = member(members, branch, "현재 신청 없음");
        archive(jdbc, legacyOnly, "아아", "구버전 기록만 남음");
        assertThat(beverages.findMyDrink(legacyOnly).items()).isEmpty();
        assertThat(beverages.findMyDrink(legacyOnly).updatedAt()).isNull();

        Long spelling = member(members, branch, "시작시 명칭 수정 금지");
        archive(jdbc, spelling, "해독쥬스(반컵)", "보관할 원본 메모");
        beverages.updateDrink(spelling, request(item("해독쥬스(반컵)", "현재 저장된 메모")));

        addAttendanceAndLeaveSentinels(context, branch.getId(), replaced);
        return List.of(deletedOne, replaced, deletedAll, unchanged, duplicates, legacyOnly, spelling);
    }

    private Long member(MemberRepository members, Branch branch, String name) {
        return members.save(new Member(
                branch.getId(), name, "test-password", MemberRole.MEMBER,
                null, LocalDate.of(2026, 10, 1), null
        )).getId();
    }

    private void addAttendanceAndLeaveSentinels(ConfigurableApplicationContext context, Long branchId, Long memberId) {
        Long present = context.getBean(AttendanceStatusTypeRepository.class).findByName("출석").orElseThrow().getId();
        context.getBean(AttendanceRepository.class).save(new Attendance(
                new AttendanceReferenceInformation(memberId, branchId, present, memberId),
                new AttendanceSlotInformation(LocalDate.of(2026, 10, 21), 1, null)
        ));
        context.getBean(LeaveRequestRepository.class).save(new LeaveRequest(
                memberId, branchId, LocalDate.of(2026, 10, 22), LeaveType.MORNING
        ));
        context.getBean(FixedLeaveRepository.class).save(new FixedLeave(
                memberId, branchId, DayOfWeek.SATURDAY, "1,2,3", "알바", true
        ));
    }

    private void createLegacyTables(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE beverage_preferences (
                    id BIGINT PRIMARY KEY,
                    member_id BIGINT NOT NULL,
                    drinks TEXT,
                    notes TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE beverage_preference_items (
                    id BIGINT PRIMARY KEY,
                    beverage_preference_id BIGINT NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    note TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE beverage_preference_drink_notes (
                    beverage_preference_id BIGINT NOT NULL,
                    drink_name VARCHAR(255) NOT NULL,
                    note TEXT,
                    PRIMARY KEY (beverage_preference_id, drink_name)
                )
                """);
    }

    private void archive(JdbcTemplate jdbc, Long memberId, String name, String note) {
        jdbc.update("""
                INSERT INTO beverage_preferences (id, member_id, drinks, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, TIMESTAMP '2026-08-01 00:00:00', TIMESTAMP '2026-08-02 00:00:00')
                """, memberId, memberId, name, note);
        jdbc.update("""
                INSERT INTO beverage_preference_items (id, beverage_preference_id, name, note)
                VALUES (?, ?, ?, ?)
                """, memberId, memberId, name, note);
        jdbc.update("""
                INSERT INTO beverage_preference_drink_notes (beverage_preference_id, drink_name, note)
                VALUES (?, ?, ?)
                """, memberId, name, note);
    }

    private Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        rows.put("beverage_items", jdbc.queryForList("""
                SELECT id, member_id, name, CAST(note AS VARCHAR) AS note, created_at, updated_at
                FROM beverage_items ORDER BY id
                """));
        rows.put("beverage_preference_audits", jdbc.queryForList("SELECT * FROM beverage_preference_audits ORDER BY id"));
        rows.put("beverage_preferences", jdbc.queryForList("""
                SELECT id, member_id, CAST(drinks AS VARCHAR) AS drinks, CAST(notes AS VARCHAR) AS notes,
                       created_at, updated_at FROM beverage_preferences ORDER BY id
                """));
        rows.put("beverage_preference_items", jdbc.queryForList("""
                SELECT id, beverage_preference_id, name, CAST(note AS VARCHAR) AS note
                FROM beverage_preference_items ORDER BY id
                """));
        rows.put("beverage_preference_drink_notes", jdbc.queryForList("""
                SELECT beverage_preference_id, drink_name, CAST(note AS VARCHAR) AS note
                FROM beverage_preference_drink_notes ORDER BY beverage_preference_id, drink_name
                """));
        rows.put("attendances", jdbc.queryForList("""
                SELECT id, member_id, branch_id, attendance_status_type_id, marked_by_member_id,
                       attendance_date, slot, CAST(custom_status_text AS VARCHAR) AS custom_status_text,
                       created_at, updated_at FROM attendances ORDER BY id
                """));
        rows.put("leave_requests", jdbc.queryForList("SELECT * FROM leave_requests ORDER BY id"));
        rows.put("fixed_leaves", jdbc.queryForList("""
                SELECT id, member_id, branch_id, day_of_week, CAST(slots AS VARCHAR) AS slots, reason,
                       active, start_date, stop_date, created_at, updated_at FROM fixed_leaves ORDER BY id
                """));
        return rows;
    }

    private Map<Long, BeveragePreferenceResponse> readPreferences(ConfigurableApplicationContext context, List<Long> memberIds) {
        Map<Long, BeveragePreferenceResponse> responses = new LinkedHashMap<>();
        BeverageService beverages = context.getBean(BeverageService.class);
        memberIds.forEach(memberId -> responses.put(memberId, beverages.findMyDrink(memberId)));
        return responses;
    }

    private BeverageItemRequest item(String name, String note) {
        return new BeverageItemRequest(name, note);
    }

    private BeverageRequest request(BeverageItemRequest... items) {
        return new BeverageRequest(null, null, null, List.of(items));
    }
}
