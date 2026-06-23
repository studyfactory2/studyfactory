package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.SpecialLeave;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SpecialLeaveResponse(
        Long id,
        Long memberId,
        Long branchId,
        LocalDate leaveDate,
        String slots,
        String reason,
        String customReason,
        boolean recurring,
        Long createdByMemberId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SpecialLeaveResponse from(SpecialLeave specialLeave) {
        return new SpecialLeaveResponse(
                specialLeave.getId(),
                specialLeave.getMemberId(),
                specialLeave.getBranchId(),
                specialLeave.getLeaveDate(),
                specialLeave.getSlots(),
                specialLeave.getReason(),
                specialLeave.getCustomReason(),
                specialLeave.isRecurring(),
                specialLeave.getCreatedByMemberId(),
                specialLeave.getCreatedAt(),
                specialLeave.getUpdatedAt()
        );
    }
}
