package com.example.studyfactory.domain.attendance.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Embedded;
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
        name = "attendances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "attendance_date", "slot"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseEntity {

    public static final String FIXED_LEAVE_CANCELLATION_MARKER = "FIXED_LEAVE_CANCELLED";

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

    public Long getMemberId() {
        return referenceInformation.getMemberId();
    }

    public Long getBranchId() {
        return referenceInformation.getBranchId();
    }

    public Long getAttendanceStatusTypeId() {
        return referenceInformation.getAttendanceStatusTypeId();
    }

    public Long getMarkedByMemberId() {
        return referenceInformation.getMarkedByMemberId();
    }

    public LocalDate getAttendanceDate() {
        return slotInformation.getAttendanceDate();
    }

    public int getSlot() {
        return slotInformation.getSlot();
    }

    public String getCustomStatusText() {
        return slotInformation.getCustomStatusText();
    }
}
