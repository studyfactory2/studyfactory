package com.example.studyfactory.domain.room.dto;

import com.example.studyfactory.domain.room.entity.Room;
import java.util.List;

public record RoomLayoutResponse(
        Long id,
        Long branchId,
        String name,
        int rows,
        int cols,
        List<RoomLayoutItemResponse> items
) {

    public static RoomLayoutResponse from(Room room, List<RoomLayoutItemResponse> items) {
        return new RoomLayoutResponse(
                room.getId(),
                room.getBranchId(),
                room.getName(),
                room.getRows(),
                room.getCols(),
                items
        );
    }
}
