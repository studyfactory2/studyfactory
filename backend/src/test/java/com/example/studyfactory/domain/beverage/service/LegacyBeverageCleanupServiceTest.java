package com.example.studyfactory.domain.beverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("회원 삭제 시 이전 버전 음료 데이터 정리 테스트")
class LegacyBeverageCleanupServiceTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private LegacyBeverageCleanupService cleanup;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        cleanup = new LegacyBeverageCleanupService(jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    @DisplayName("이전 테이블이 없는 신규 설치에서는 아무것도 변경하지 않는다")
    void skipsAbsentLegacyTables() {
        assertThatCode(() -> transaction.executeWithoutResult(status -> cleanup.deleteByMemberId(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자식 테이블이 없는 이전 버전에서도 선택한 회원의 설정만 삭제한다")
    void deletesOnlyTargetPreferencesWhenChildrenAreAbsent() {
        createPreferences();

        transaction.executeWithoutResult(status -> cleanup.deleteByMemberId(1L));

        assertThat(jdbc.queryForList("select member_id from beverage_preferences order by id", Long.class))
                .containsExactly(2L);
    }

    @Test
    @DisplayName("외래 키가 있는 이전 음료 항목과 메모를 먼저 삭제하고 다른 회원은 보존한다")
    void deletesOwnedChildrenBeforePreferences() {
        createPreferences();
        createChildren();

        transaction.executeWithoutResult(status -> cleanup.deleteByMemberId(1L));

        assertThat(jdbc.queryForList("select member_id from beverage_preferences", Long.class))
                .containsExactly(2L);
        assertThat(jdbc.queryForList("select beverage_preference_id from beverage_preference_items", Long.class))
                .containsExactly(20L);
        assertThat(jdbc.queryForList("select beverage_preference_id from beverage_preference_drink_notes", Long.class))
                .containsExactly(20L);
    }

    @Test
    @DisplayName("계정 삭제 트랜잭션이 실패하면 이전 음료 설정과 자식 기록도 복원된다")
    void rollsBackAllLegacyCleanupWithCaller() {
        createPreferences();
        createChildren();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            cleanup.deleteByMemberId(1L);
            throw new IllegalStateException("member deletion failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from beverage_preferences", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from beverage_preference_items", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from beverage_preference_drink_notes", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 스키마에만 있는 이전 테이블은 정리 대상으로 보지 않는다")
    void ignoresTablesOutsideCurrentSchema() {
        jdbc.execute("create schema archived");
        jdbc.execute("create table archived.beverage_preferences (id bigint primary key, member_id bigint)");
        jdbc.update("insert into archived.beverage_preferences values (10, 1)");

        transaction.executeWithoutResult(status -> cleanup.deleteByMemberId(1L));

        assertThat(jdbc.queryForObject("select count(*) from archived.beverage_preferences", Integer.class)).isEqualTo(1);
    }

    private void createPreferences() {
        jdbc.execute("create table beverage_preferences (id bigint primary key, member_id bigint not null)");
        jdbc.update("insert into beverage_preferences values (10, 1), (11, 1), (20, 2)");
    }

    private void createChildren() {
        jdbc.execute("""
                create table beverage_preference_items (
                    id bigint primary key, beverage_preference_id bigint not null,
                    foreign key (beverage_preference_id) references beverage_preferences(id))
                """);
        jdbc.execute("""
                create table beverage_preference_drink_notes (
                    beverage_preference_id bigint not null, drink_name varchar(100),
                    foreign key (beverage_preference_id) references beverage_preferences(id))
                """);
        jdbc.update("insert into beverage_preference_items values (100, 10), (110, 11), (200, 20)");
        jdbc.update("insert into beverage_preference_drink_notes values (10, '아아'), (11, '선식'), (20, '아아')");
    }
}
