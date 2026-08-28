package com.example.studyfactory.domain.studyPresence.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceDoorQrResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceQrRequest;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceStatusResponse;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-presence")
public class StudyPresenceController {

    private final StudyPresenceService studyPresenceService;

    @GetMapping("/me")
    public StudyPresenceStatusResponse findMine(@CurrentMember Long memberId) {
        return StudyPresenceStatusResponse.from(studyPresenceService.findActive(memberId));
    }

    @GetMapping("/door-qr")
    public ResponseEntity<StudyPresenceDoorQrResponse> findDoorQr(@CurrentMember Long currentMemberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(studyPresenceService.findDoorQr(currentMemberId));
    }

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public StudyPresenceResponse checkIn(
            @CurrentMember Long memberId,
            @Valid @RequestBody StudyPresenceQrRequest request
    ) {
        return StudyPresenceResponse.from(studyPresenceService.checkIn(memberId, request.qrToken()));
    }

    @PostMapping("/check-out")
    public StudyPresenceResponse checkOut(
            @CurrentMember Long memberId,
            @Valid @RequestBody StudyPresenceQrRequest request
    ) {
        return StudyPresenceResponse.from(studyPresenceService.checkOut(memberId, request.qrToken()));
    }
}
