package com.example.studyfactory.domain.member.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.dto.RegistrationCodeIssueResponse;
import com.example.studyfactory.domain.member.service.PreRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/pre-registrations")
public class PreRegistrationController {

    private final PreRegistrationService preRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreRegistrationResponse create(
            @CurrentMember Long currentMemberId,
            @Valid @RequestBody PreRegistrationCreateRequest request
    ) {
        return preRegistrationService.create(currentMemberId, request);
    }

    @GetMapping("/pending")
    public List<PreRegistrationResponse> findPending(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) Long branchId
    ) {
        return preRegistrationService.findPending(currentMemberId, branchId);
    }

    @PatchMapping("/{memberId}")
    public PreRegistrationResponse update(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @Valid @RequestBody PreRegistrationCreateRequest request
    ) {
        return preRegistrationService.update(currentMemberId, memberId, request);
    }

    @PostMapping("/{memberId}/registration-code")
    public RegistrationCodeIssueResponse reissueRegistrationCode(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId
    ) {
        return preRegistrationService.reissueRegistrationCode(currentMemberId, memberId);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId
    ) {
        preRegistrationService.delete(currentMemberId, memberId);
    }
}
