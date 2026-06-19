package com.example.studyfactory.domain.staffSchedule.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "staff_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_member_id", nullable = false)
    private Long staffMemberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffScheduleShift shift;

    @Column(nullable = false, length = 50)
    private String taskType;

    public StaffSchedule(
            Long staffMemberId,
            Long branchId,
            LocalDate weekStart,
            DayOfWeek dayOfWeek,
            StaffScheduleShift shift,
            String taskType
    ) {
        this.staffMemberId = staffMemberId;
        this.branchId = branchId;
        this.weekStart = weekStart;
        this.dayOfWeek = dayOfWeek;
        this.shift = shift;
        this.taskType = taskType;
    }
}
