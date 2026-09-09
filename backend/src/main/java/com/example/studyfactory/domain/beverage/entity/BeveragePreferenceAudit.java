package com.example.studyfactory.domain.beverage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stable lifecycle timestamps for a member's beverage preference.
 *
 * <p>The item rows are intentionally replaceable and may be empty, so their
 * auditing columns cannot describe when the preference was first submitted or
 * last changed.</p>
 */
@Getter
@Entity
@Table(
        name = "beverage_preference_audits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_beverage_preference_audits_member",
                columnNames = "member_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeveragePreferenceAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BeveragePreferenceAudit(Long memberId, Instant createdAt, Instant updatedAt) {
        this.memberId = memberId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void touch(Instant changedAt) {
        this.updatedAt = changedAt;
    }
}
