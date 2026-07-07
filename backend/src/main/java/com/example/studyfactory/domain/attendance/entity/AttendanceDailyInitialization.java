package com.example.studyfactory.domain.attendance.entity;

import com.example.studyfactory.common.BaseEntity;
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
        name = "attendance_daily_initializations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "attendance_date"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceDailyInitialization extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "initialized_by_member_id", nullable = false)
    private Long initializedByMemberId;

    public AttendanceDailyInitialization(Long memberId, Long branchId, LocalDate attendanceDate, Long initializedByMemberId) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.attendanceDate = attendanceDate;
        this.initializedByMemberId = initializedByMemberId;
    }
}
