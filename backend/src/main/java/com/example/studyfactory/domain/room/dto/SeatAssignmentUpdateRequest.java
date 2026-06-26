package com.example.studyfactory.domain.room.dto;

import jakarta.validation.constraints.Positive;

public record SeatAssignmentUpdateRequest(
        @Positive(message = "좌석번호는 1 이상이어야 합니다.")
        Integer seatNumber
) {
}
