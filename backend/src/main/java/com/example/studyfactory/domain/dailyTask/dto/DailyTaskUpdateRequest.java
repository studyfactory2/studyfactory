package com.example.studyfactory.domain.dailyTask.dto;

import java.time.LocalDate;

public record DailyTaskUpdateRequest(LocalDate date, Long expectedRevision, String content, Boolean completed) {
}
