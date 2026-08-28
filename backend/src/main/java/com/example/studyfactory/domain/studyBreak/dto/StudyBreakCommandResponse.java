package com.example.studyfactory.domain.studyBreak.dto;

public record StudyBreakCommandResponse(
        boolean changed,
        StudyBreakStatusResponse status
) {
}
