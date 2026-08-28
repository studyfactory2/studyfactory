package com.example.studyfactory.domain.studyPresence.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceDoorQrResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceHistoryResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceLiveResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceManagerSessionResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceQrRequest;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceSelfHistoryResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceStatusResponse;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceQueryService;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-presence")
public class StudyPresenceController {

    private final StudyPresenceService studyPresenceService;
    private final StudyPresenceQueryService studyPresenceQueryService;

    @GetMapping("/me")
    public ResponseEntity<StudyPresenceStatusResponse> findMine(@CurrentMember Long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(StudyPresenceStatusResponse.from(studyPresenceService.findActive(memberId)));
    }

    @GetMapping("/me/history")
    public ResponseEntity<StudyPresenceSelfHistoryResponse> findMyHistory(
            @CurrentMember Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyPresenceQueryService.findMyHistory(memberId, from, to));
    }

    @GetMapping("/door-qr")
    public ResponseEntity<StudyPresenceDoorQrResponse> findDoorQr(@CurrentMember Long currentMemberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(studyPresenceService.findDoorQr(currentMemberId));
    }

    @GetMapping("/live")
    public ResponseEntity<StudyPresenceLiveResponse> findLive(@CurrentMember Long currentMemberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyPresenceQueryService.findLive(currentMemberId));
    }

    @GetMapping("/history")
    public ResponseEntity<StudyPresenceHistoryResponse> findDailyHistory(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyPresenceQueryService.findDailyHistory(currentMemberId, date));
    }

    @GetMapping("/members/{memberId}/history")
    public ResponseEntity<StudyPresenceHistoryResponse> findMemberHistory(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyPresenceQueryService.findMemberHistory(
                        currentMemberId,
                        memberId,
                        from,
                        to
                ));
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

    @PostMapping("/sessions/{sessionId}/manual-check-out")
    public StudyPresenceManagerSessionResponse managerCheckOut(
            @CurrentMember Long currentMemberId,
            @PathVariable Long sessionId
    ) {
        return studyPresenceService.managerCheckOut(currentMemberId, sessionId);
    }
}
