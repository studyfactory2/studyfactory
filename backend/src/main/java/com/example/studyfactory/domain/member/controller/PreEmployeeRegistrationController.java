package com.example.studyfactory.domain.member.controller;

import com.example.studyfactory.domain.member.dto.PreEmployeeRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreEmployeeRegistrationResponse;
import com.example.studyfactory.domain.member.service.PreEmployeeRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pre-employee-registrations")
public class PreEmployeeRegistrationController {

    private final PreEmployeeRegistrationService preEmployeeRegistrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreEmployeeRegistrationResponse create(@RequestBody PreEmployeeRegistrationCreateRequest request) {
        return preEmployeeRegistrationService.create(request);
    }
}
