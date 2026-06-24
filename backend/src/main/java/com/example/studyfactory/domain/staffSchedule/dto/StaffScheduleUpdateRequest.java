package com.example.studyfactory.domain.staffSchedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StaffScheduleUpdateRequest(
        @NotNull List<@Valid StaffScheduleCellRequest> schedules
) {
}
