package com.example.studyfactory.domain.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceReferenceInformation {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "attendance_status_type_id", nullable = false)
    private Long attendanceStatusTypeId;

    @Column(name = "marked_by_member_id")
    private Long markedByMemberId;

    public AttendanceReferenceInformation(
            Long memberId,
            Long branchId,
            Long attendanceStatusTypeId,
            Long markedByMemberId
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.attendanceStatusTypeId = attendanceStatusTypeId;
        this.markedByMemberId = markedByMemberId;
    }
}
