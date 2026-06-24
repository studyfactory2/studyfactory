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

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffScheduleShift shift;

    @Column(nullable = false, length = 50)
    private String taskType;

    @Column(nullable = false, length = 50)
    private String workerName;

    public StaffSchedule(
            Long branchId,
            DayOfWeek dayOfWeek,
            StaffScheduleShift shift,
            String taskType,
            String workerName
    ) {
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.shift = shift;
        this.taskType = taskType;
        this.workerName = workerName;
    }
}
