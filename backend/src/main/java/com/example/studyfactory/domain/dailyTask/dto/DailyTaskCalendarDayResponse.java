package com.example.studyfactory.domain.dailyTask.dto;

import java.time.LocalDate;

public record DailyTaskCalendarDayResponse(LocalDate date, long totalCount, long completedCount) {
}
