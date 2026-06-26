package com.example.studyfactory.domain.room.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.room.dto.SeatAssignmentUpdateRequest;
import com.example.studyfactory.domain.room.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    @PatchMapping("/assignments/members/{memberId}")
    public MemberResponse updateAssignment(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @Valid @RequestBody SeatAssignmentUpdateRequest request
    ) {
        return seatService.updateAssignment(currentMemberId, memberId, request);
    }
}
