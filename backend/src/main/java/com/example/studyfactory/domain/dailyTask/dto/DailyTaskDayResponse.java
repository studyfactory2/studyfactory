package com.example.studyfactory.domain.dailyTask.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyTaskDayResponse(Long memberId, LocalDate date, long revision, List<DailyTaskItemResponse> items) {
}
