package com.example.studyfactory.domain.dailyTask.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DailyTaskCreateRequest(LocalDate date, Long expectedRevision, String content, UUID clientRequestId) {
}
