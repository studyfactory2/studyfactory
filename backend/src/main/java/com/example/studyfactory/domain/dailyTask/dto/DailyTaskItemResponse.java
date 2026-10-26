package com.example.studyfactory.domain.dailyTask.dto;

public record DailyTaskItemResponse(Long id, String content, boolean completed, int sortOrder) {
}
