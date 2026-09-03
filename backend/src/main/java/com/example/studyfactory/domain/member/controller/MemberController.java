package com.example.studyfactory.domain.member.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.MemberUpdateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public List<MemberResponse> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long branchId
    ) {
        return memberService.findAll(name, branchId);
    }

    @GetMapping("/me")
    public MemberResponse findMe(@CurrentMember Long currentMemberId) {
        return memberService.findMe(currentMemberId);
    }

    @GetMapping("/pre-registrations/pending")
    public List<MemberResponse> findPendingPreRegistrations() {
        return memberService.findPendingPreRegistrations();
    }

    @PatchMapping("/{memberId}")
    public MemberResponse update(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        return memberService.update(currentMemberId, memberId, request);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId
    ) {
        memberService.delete(currentMemberId, memberId);
    }

    @PostMapping("/pre-registration/verify")
    public List<PreRegistrationVerifyResponse> verifyPreRegistration(
            @Valid @RequestBody PreRegistrationVerifyRequest request
    ) {
        return memberService.verifyPreRegistration(request);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberSignupResponse signup(@Valid @RequestBody MemberSignupRequest request) {
        return memberService.signup(request);
    }
}
