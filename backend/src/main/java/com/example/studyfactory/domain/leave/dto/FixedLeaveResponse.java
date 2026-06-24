package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.FixedLeave;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

public record FixedLeaveResponse(
        Long id,
        Long memberId,
        Long branchId,
        DayOfWeek dayOfWeek,
        String slots,
        String reason,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static FixedLeaveResponse from(FixedLeave fixedLeave) {
        return new FixedLeaveResponse(
                fixedLeave.getId(),
                fixedLeave.getMemberId(),
                fixedLeave.getBranchId(),
                fixedLeave.getDayOfWeek(),
                fixedLeave.getSlots(),
                fixedLeave.getReason(),
                fixedLeave.isActive(),
                fixedLeave.getCreatedAt(),
                fixedLeave.getUpdatedAt()
        );
    }
}
