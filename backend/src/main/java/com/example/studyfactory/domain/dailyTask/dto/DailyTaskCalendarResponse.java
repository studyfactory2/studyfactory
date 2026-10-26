package com.example.studyfactory.domain.dailyTask.dto;

import java.util.List;

public record DailyTaskCalendarResponse(Long memberId, String month, List<DailyTaskCalendarDayResponse> days) {
}
