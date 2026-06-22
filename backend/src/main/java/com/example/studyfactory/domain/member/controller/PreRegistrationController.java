package com.example.studyfactory.domain.member.controller;

import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.service.PreRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pre-registrations")
public class PreRegistrationController {

    private final PreRegistrationService preRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreRegistrationResponse create(@Valid @RequestBody PreRegistrationCreateRequest request) {
        return preRegistrationService.create(request);
    }
}
