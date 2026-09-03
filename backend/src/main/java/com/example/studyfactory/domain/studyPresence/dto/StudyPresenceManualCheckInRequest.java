package com.example.studyfactory.domain.studyPresence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StudyPresenceManualCheckInRequest(
        @NotNull(message = "입실 시각은 필수입니다.")
        Instant checkedInAt,

        @NotBlank(message = "수동 입실 사유는 필수입니다.")
        @Size(max = 200, message = "수동 입실 사유는 200자를 넘을 수 없습니다.")
        String reason
) {
}
