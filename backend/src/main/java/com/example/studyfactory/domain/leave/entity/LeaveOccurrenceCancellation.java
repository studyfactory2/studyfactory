package com.example.studyfactory.domain.leave.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Suppresses only an existing source occurrence; later leave requests remain independent. */
@Getter
@Entity
@Table(name = "leave_occurrence_cancellations",
        check = @CheckConstraint(name = "ck_leave_occurrence_cancellation_slot", constraint = "slot between 1 and 7"),
        uniqueConstraints = @UniqueConstraint(name = "uk_leave_occurrence_cancellation",
                columnNames = {"source_type", "source_id", "leave_date", "slot"}),
        indexes = {
                @Index(name = "idx_leave_cancellation_member_date", columnList = "member_id, leave_date"),
                @Index(name = "idx_leave_cancellation_branch_date", columnList = "branch_id, leave_date")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveOccurrenceCancellation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false)
    private Long branchId;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private LeaveSourceType sourceType;
    @Column(name = "source_id", nullable = false)
    private Long sourceId;
    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;
    @Column(nullable = false)
    private Integer slot;
    @Column(nullable = false)
    private Long createdByMemberId;

    public LeaveOccurrenceCancellation(Long memberId, Long branchId, LeaveSourceType sourceType,
                                       Long sourceId, LocalDate leaveDate, Integer slot, Long createdByMemberId) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.leaveDate = leaveDate;
        this.slot = slot;
        this.createdByMemberId = createdByMemberId;
    }
}
