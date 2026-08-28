package com.example.studyfactory.domain.studyTime.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.studyTime.dto.StudyTimeReportResponse;
import com.example.studyfactory.domain.studyTime.service.StudyTimeReportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-time")
public class StudyTimeReportController {

    private final StudyTimeReportService studyTimeReportService;

    @GetMapping("/me/report")
    public ResponseEntity<StudyTimeReportResponse> findMine(
            @CurrentMember Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyTimeReportService.findMine(memberId, from, to));
    }

    @GetMapping("/members/{memberId}/report")
    public ResponseEntity<StudyTimeReportResponse> findMemberReport(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(studyTimeReportService.findForManager(currentMemberId, memberId, from, to));
    }
}
