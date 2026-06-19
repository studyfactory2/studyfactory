package com.example.studyfactory.domain.attendance.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "attendances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "attendance_date", "slot"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private AttendanceReferenceInformation referenceInformation;

    @Embedded
    private AttendanceSlotInformation slotInformation;

    public Attendance(
            AttendanceReferenceInformation referenceInformation,
            AttendanceSlotInformation slotInformation
    ) {
        this.referenceInformation = referenceInformation;
        this.slotInformation = slotInformation;
    }
}
