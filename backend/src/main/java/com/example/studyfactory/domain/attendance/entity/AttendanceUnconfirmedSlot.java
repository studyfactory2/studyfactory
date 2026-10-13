package com.example.studyfactory.domain.attendance.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Explicitly clears one board cell without deleting its underlying leave records. */
@Getter
@Entity
@Table(
        name = "attendance_unconfirmed_slots",
        check = @CheckConstraint(
                name = "ck_attendance_unconfirmed_slots_slot",
                constraint = "slot between 1 and 7"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_unconfirmed_slots_member_date_slot",
                columnNames = {"member_id", "attendance_date", "slot"}
        ),
        indexes = @Index(
                name = "idx_attendance_unconfirmed_slots_branch_date",
                columnList = "branch_id, attendance_date"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceUnconfirmedSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false, updatable = false)
    private int slot;

    @Column(name = "cleared_by_member_id", updatable = false)
    private Long clearedByMemberId;

    @Column(name = "cleared_at", nullable = false, updatable = false)
    private Instant clearedAt;

    public AttendanceUnconfirmedSlot(
            Long memberId,
            Long branchId,
            LocalDate attendanceDate,
            int slot,
            Long clearedByMemberId,
            Instant clearedAt
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.attendanceDate = attendanceDate;
        this.slot = slot;
        this.clearedByMemberId = clearedByMemberId;
        this.clearedAt = clearedAt;
    }
}
