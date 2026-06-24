package com.example.studyfactory.domain.staffSchedule.dto;

import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import com.example.studyfactory.domain.staffSchedule.entity.StaffScheduleShift;
import java.time.DayOfWeek;

public record StaffScheduleResponse(
        Long id,
        Long branchId,
        DayOfWeek dayOfWeek,
        StaffScheduleShift shift,
        String taskType,
        String workerName
) {

    public static StaffScheduleResponse from(StaffSchedule staffSchedule) {
        return new StaffScheduleResponse(
                staffSchedule.getId(),
                staffSchedule.getBranchId(),
                staffSchedule.getDayOfWeek(),
                staffSchedule.getShift(),
                staffSchedule.getTaskType(),
                staffSchedule.getWorkerName()
        );
    }

    public static StaffScheduleResponse blank(Long branchId, DayOfWeek dayOfWeek, StaffScheduleShift shift, String taskType) {
        return new StaffScheduleResponse(null, branchId, dayOfWeek, shift, taskType, "");
    }
}
