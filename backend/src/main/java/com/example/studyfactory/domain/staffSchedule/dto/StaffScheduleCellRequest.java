package com.example.studyfactory.domain.staffSchedule.dto;

import com.example.studyfactory.domain.staffSchedule.entity.StaffScheduleShift;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;

public record StaffScheduleCellRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull StaffScheduleShift shift,
        @NotBlank String taskType,
        @NotNull @Size(max = 50) String workerName
) {
}
