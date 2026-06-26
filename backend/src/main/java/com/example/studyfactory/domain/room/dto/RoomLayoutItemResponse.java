package com.example.studyfactory.domain.room.dto;

import com.example.studyfactory.domain.room.entity.Seat;
import com.example.studyfactory.domain.room.entity.SeatType;

public record RoomLayoutItemResponse(
        Long id,
        SeatType type,
        Integer number,
        Long memberId,
        int x,
        int y
) {

    public static RoomLayoutItemResponse from(Seat seat) {
        return new RoomLayoutItemResponse(
                seat.getId(),
                seat.getType(),
                seat.getNumber(),
                seat.getMemberId(),
                seat.getGridCol(),
                seat.getGridRow()
        );
    }
}
