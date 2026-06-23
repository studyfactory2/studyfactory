package com.example.studyfactory.domain.certification.controller;

import com.example.studyfactory.domain.certification.dto.CertificationCreateRequest;
import com.example.studyfactory.domain.certification.dto.CertificationResponse;
import com.example.studyfactory.domain.certification.service.CertificationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/certifications")
public class CertificationController {

    private final CertificationService certificationService;

    @GetMapping
    public List<CertificationResponse> findAll() {
        return certificationService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationResponse create(@Valid @RequestBody CertificationCreateRequest request) {
        return certificationService.create(request);
    }
}
