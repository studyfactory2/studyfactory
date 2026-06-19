package com.example.studyfactory.domain.leave.dto;

import com.example.studyfactory.domain.leave.entity.LeaveType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LeaveCreateRequest(
        @NotNull(message = "휴무 날짜는 필수입니다.")
        LocalDate leaveDate,

        @NotNull(message = "휴무 종류는 필수입니다.")
        LeaveType leaveType
) {
}
