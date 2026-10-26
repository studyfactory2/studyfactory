package com.example.studyfactory.domain.weeklyPlan.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "shared_plan_weeks",
        check = @CheckConstraint(name = "ck_shared_plan_weeks_revision", constraint = "revision >= 0"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_plan_weeks_member_week",
                columnNames = {"member_id", "week_start_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedPlanWeek extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false)
    private long revision;

    @Column(name = "legacy_save_protected", nullable = false)
    private boolean legacySaveProtected;

    public SharedPlanWeek(Long memberId, LocalDate weekStartDate) {
        this.memberId = memberId;
        this.weekStartDate = weekStartDate;
    }

    public void advance(boolean protectLegacySave) {
        revision = Math.incrementExact(revision);
        legacySaveProtected = legacySaveProtected || protectLegacySave;
    }
}
