package com.example.studyfactory.domain.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceSlotInformation {

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(nullable = false)
    private int slot;

    @Column(columnDefinition = "text")
    private String customStatusText;

    public AttendanceSlotInformation(LocalDate attendanceDate, int slot, String customStatusText) {
        this.attendanceDate = attendanceDate;
        this.slot = slot;
        this.customStatusText = customStatusText;
    }
}
