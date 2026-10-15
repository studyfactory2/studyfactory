package com.example.studyfactory.domain.beverage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Removes only the deleted member's data from optional, pre-migration beverage tables. */
@Service
@RequiredArgsConstructor
public class LegacyBeverageCleanupService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByMemberId(Long memberId) {
        if (!hasColumn("beverage_preferences", "id")
                || !hasColumn("beverage_preferences", "member_id")) {
            return;
        }

        if (hasColumn("beverage_preference_items", "beverage_preference_id")) {
            jdbcTemplate.update("""
                    delete from beverage_preference_items
                    where beverage_preference_id in
                        (select id from beverage_preferences where member_id = ?)
                    """, memberId);
        }
        if (hasColumn("beverage_preference_drink_notes", "beverage_preference_id")) {
            jdbcTemplate.update("""
                    delete from beverage_preference_drink_notes
                    where beverage_preference_id in
                        (select id from beverage_preferences where member_id = ?)
                    """, memberId);
        }
        jdbcTemplate.update("delete from beverage_preferences where member_id = ?", memberId);
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = current_schema()
                  and lower(table_name) = ? and lower(column_name) = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
