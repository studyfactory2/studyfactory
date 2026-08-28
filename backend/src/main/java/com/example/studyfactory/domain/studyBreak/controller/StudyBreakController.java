package com.example.studyfactory.domain.studyBreak.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakCommandResponse;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakStatusResponse;
import com.example.studyfactory.domain.studyBreak.service.StudyBreakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-breaks/me")
public class StudyBreakController {

    private final StudyBreakService studyBreakService;

    @GetMapping("/status")
    public ResponseEntity<StudyBreakStatusResponse> findStatus(@CurrentMember Long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyBreakService.findStatus(memberId));
    }

    @PostMapping("/start")
    public ResponseEntity<StudyBreakCommandResponse> start(@CurrentMember Long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyBreakService.start(memberId));
    }

    @PostMapping("/stop")
    public ResponseEntity<StudyBreakCommandResponse> stop(@CurrentMember Long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyBreakService.stop(memberId));
    }
}
