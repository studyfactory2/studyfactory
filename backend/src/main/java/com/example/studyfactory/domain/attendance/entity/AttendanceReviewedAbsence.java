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

/** A staff-reviewed absence, deliberately isolated from legacy attendance rows. */
@Getter
@Entity
@Table(
        name = "attendance_reviewed_absences",
        check = @CheckConstraint(
                name = "ck_attendance_reviewed_absences_slot",
                constraint = "slot between 1 and 7"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_reviewed_absences_member_date_slot",
                columnNames = {"member_id", "attendance_date", "slot"}
        ),
        indexes = @Index(
                name = "idx_attendance_reviewed_absences_branch_date",
                columnList = "branch_id, attendance_date"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceReviewedAbsence {

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

    @Column(name = "reviewed_by_member_id", updatable = false)
    private Long reviewedByMemberId;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    public AttendanceReviewedAbsence(
            Long memberId,
            Long branchId,
            LocalDate attendanceDate,
            int slot,
            Long reviewedByMemberId,
            Instant reviewedAt
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.attendanceDate = attendanceDate;
        this.slot = slot;
        this.reviewedByMemberId = reviewedByMemberId;
        this.reviewedAt = reviewedAt;
    }
}
