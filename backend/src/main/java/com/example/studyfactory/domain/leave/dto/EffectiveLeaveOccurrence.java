package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveSourceType;
import com.example.studyfactory.domain.leave.entity.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record EffectiveLeaveOccurrence(Long memberId, Long branchId, LeaveSourceType sourceType, Long sourceId,
                                       LocalDate date, Set<Integer> slots, String reason, LeaveType leaveType,
                                       LocalDateTime createdAt) {
    public EffectiveLeaveOccurrence {
        slots = Set.copyOf(slots);
    }
}
