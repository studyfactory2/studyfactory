package com.example.studyfactory.domain.beverage.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 기존 음료 설정 구조를 회원별 음료 항목 구조로 옮긴다. 기존 테이블은 검증 전까지 보존한다. */
@Component
@RequiredArgsConstructor
class BeverageItemSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        renameDetoxDrink();

        if (!hasTable("beverage_items") || !hasTable("beverage_preferences")) {
            return;
        }

        migrateIntermediateItems();
        migrateLegacyPreferences();
    }

    /** 화면 표기와 저장 값을 해독으로 통일한다. 기존 데이터는 앱 시작 시 한 번 안전하게 치환된다. */
    private void renameDetoxDrink() {
        if (hasTable("beverage_preference_drink_notes")) {
            jdbcTemplate.update("""
                    DELETE FROM beverage_preference_drink_notes legacy
                    WHERE legacy.drink_name LIKE '%해독쥬스%'
                      AND EXISTS (
                          SELECT 1
                          FROM beverage_preference_drink_notes current
                          WHERE current.beverage_preference_id = legacy.beverage_preference_id
                            AND current.drink_name = REPLACE(legacy.drink_name, '해독쥬스', '해독')
                      )
                    """);
            jdbcTemplate.update("""
                    UPDATE beverage_preference_drink_notes
                    SET drink_name = REPLACE(drink_name, '해독쥬스', '해독')
                    WHERE drink_name LIKE '%해독쥬스%'
                    """);
        }
        if (hasTable("beverage_preferences") && hasColumn("beverage_preferences", "drinks")) {
            jdbcTemplate.update("""
                    UPDATE beverage_preferences
                    SET drinks = REPLACE(drinks, '해독쥬스', '해독'), updated_at = CURRENT_TIMESTAMP
                    WHERE drinks LIKE '%해독쥬스%'
                    """);
        }
        if (hasTable("beverage_items")) {
            jdbcTemplate.update("""
                    UPDATE beverage_items
                    SET name = REPLACE(name, '해독쥬스', '해독'), updated_at = CURRENT_TIMESTAMP
                    WHERE name LIKE '%해독쥬스%'
                    """);
        }
    }

    private void migrateIntermediateItems() {
        if (!hasTable("beverage_preference_items")) {
            return;
        }
        jdbcTemplate.queryForList("""
                        SELECT p.member_id, i.name, i.note
                        FROM beverage_preference_items i
                        JOIN beverage_preferences p ON p.id = i.beverage_preference_id
                        JOIN members m ON m.id = p.member_id
                        """)
                .forEach(row -> insertIfMissing(
                        ((Number) row.get("member_id")).longValue(),
                        toText(row.get("name")),
                        toText(row.get("note"))
                ));
    }

    private void migrateLegacyPreferences() {
        if (!hasColumn("beverage_preferences", "drinks")) {
            return;
        }
        String notesColumn = hasColumn("beverage_preferences", "notes") ? "p.notes AS notes" : "NULL AS notes";
        jdbcTemplate.queryForList("SELECT p.id, p.member_id, p.drinks, " + notesColumn
                        + " FROM beverage_preferences p JOIN members m ON m.id = p.member_id")
                .forEach(row -> {
                    Long preferenceId = ((Number) row.get("id")).longValue();
                    Long memberId = ((Number) row.get("member_id")).longValue();
                    String legacyNote = toText(row.get("notes"));
                    Map<String, String> drinkNotes = readLegacyDrinkNotes(preferenceId);
                    for (String drink : toDrinks(toText(row.get("drinks")))) {
                        insertIfMissing(memberId, drink, drinkNotes.getOrDefault(drink, legacyNote));
                    }
                });
    }

    private Map<String, String> readLegacyDrinkNotes(Long preferenceId) {
        if (!hasTable("beverage_preference_drink_notes")) {
            return Map.of();
        }
        Map<String, String> notes = new LinkedHashMap<>();
        jdbcTemplate.queryForList(
                        "SELECT drink_name, note FROM beverage_preference_drink_notes WHERE beverage_preference_id = ?",
                        preferenceId
                )
                .forEach(row -> notes.put(toText(row.get("drink_name")), toText(row.get("note"))));
        return notes;
    }

    private void insertIfMissing(Long memberId, String name, String note) {
        if (name.isBlank()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM beverage_items WHERE member_id = ? AND name = ?", Integer.class, memberId, name
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO beverage_items (member_id, name, note, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                memberId, name, note.isBlank() ? null : note);
    }

    private boolean hasTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?", Integer.class, tableName.toLowerCase()
        );
        return count != null && count > 0;
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                Integer.class, tableName.toLowerCase(), columnName.toLowerCase()
        );
        return count != null && count > 0;
    }

    private List<String> toDrinks(String drinks) {
        return Arrays.stream(drinks.split("\\R|,"))
                .map(String::trim)
                .filter(drink -> !drink.isBlank())
                .distinct()
                .toList();
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
