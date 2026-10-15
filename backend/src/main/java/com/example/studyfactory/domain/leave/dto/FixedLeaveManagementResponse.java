package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.FixedLeave;
import com.example.studyfactory.domain.member.entity.Member;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FixedLeaveManagementResponse(
        Long id,
        Long memberId,
        Long branchId,
        String memberName,
        DayOfWeek dayOfWeek,
        String slots,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDate startDate,
        LocalDate stopDate
) {

    public static FixedLeaveManagementResponse from(FixedLeave fixedLeave, Member member) {
        return new FixedLeaveManagementResponse(
                fixedLeave.getId(),
                fixedLeave.getMemberId(),
                fixedLeave.getBranchId(),
                member.getName(),
                fixedLeave.getDayOfWeek(),
                fixedLeave.getSlots(),
                fixedLeave.getReason(),
                fixedLeave.getCreatedAt(),
                fixedLeave.getUpdatedAt(),
                fixedLeave.getStartDate(),
                fixedLeave.getStopDate()
        );
    }
}
