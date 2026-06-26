package com.example.studyfactory.domain.room.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.room.dto.RoomLayoutResponse;
import com.example.studyfactory.domain.room.service.RoomService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public List<RoomLayoutResponse> findLayouts(@CurrentMember Long currentMemberId, @RequestParam(required = false) Long branchId) {
        return roomService.findLayouts(currentMemberId, branchId);
    }
}
