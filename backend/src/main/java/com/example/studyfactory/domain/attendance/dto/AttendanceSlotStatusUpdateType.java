package com.example.studyfactory.domain.attendance.dto;

public enum AttendanceSlotStatusUpdateType {
    PRESENT,
    ABSENT,
    OTHER,
    UNCONFIRMED,
    /** Remove manual overrides and reveal current effective leave, not a historical snapshot. */
    RESTORE
}
