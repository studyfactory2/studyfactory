package com.example.studyfactory.domain.leave.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "fixed_leaves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedLeave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false, columnDefinition = "text")
    private String slots;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(nullable = false)
    private boolean active;

    public FixedLeave(Long memberId, Long branchId, DayOfWeek dayOfWeek, String slots, String reason, boolean active) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.slots = slots;
        this.reason = reason;
        this.active = active;
    }
}
