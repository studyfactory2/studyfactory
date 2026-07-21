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
        if (!hasTable("beverage_items") || !hasTable("beverage_preferences")) {
            return;
        }

        migrateIntermediateItems();
        migrateLegacyPreferences();
    }

    private void migrateIntermediateItems() {
        if (!hasTable("beverage_preference_items")) {
            return;
        }
        jdbcTemplate.queryForList("""
                        SELECT p.member_id, i.name, i.note
                        FROM beverage_preference_items i
                        JOIN beverage_preferences p ON p.id = i.beverage_preference_id
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
        String notesColumn = hasColumn("beverage_preferences", "notes") ? "notes" : "NULL AS notes";
        jdbcTemplate.queryForList("SELECT id, member_id, drinks, " + notesColumn + " FROM beverage_preferences")
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
