package com.example.studyfactory.domain.suggestion.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.suggestion.dto.SuggestionCreateRequest;
import com.example.studyfactory.domain.suggestion.dto.SuggestionResponse;
import com.example.studyfactory.domain.suggestion.service.SuggestionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionResponse create(@CurrentMember Long memberId, @Valid @RequestBody SuggestionCreateRequest request) {
        return suggestionService.create(memberId, request);
    }

    @GetMapping("/me")
    public List<SuggestionResponse> findMine(@CurrentMember Long memberId) {
        return suggestionService.findMine(memberId);
    }

    @GetMapping
    public List<SuggestionResponse> findAll(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) Long branchId
    ) {
        return suggestionService.findAll(currentMemberId, branchId);
    }

    @PatchMapping("/{suggestionId}/resolve")
    public SuggestionResponse resolve(@CurrentMember Long memberId, @PathVariable Long suggestionId) {
        return suggestionService.resolve(memberId, suggestionId);
    }
}
