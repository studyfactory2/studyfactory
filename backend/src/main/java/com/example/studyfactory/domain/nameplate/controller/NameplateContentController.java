package com.example.studyfactory.domain.nameplate.controller;

import com.example.studyfactory.domain.nameplate.dto.NameplateContentCreateRequest;
import com.example.studyfactory.domain.nameplate.dto.NameplateContentResponse;
import com.example.studyfactory.domain.nameplate.service.NameplateContentService;
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
@RequestMapping("/api/nameplate-contents")
public class NameplateContentController {

    private final NameplateContentService nameplateContentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NameplateContentResponse create(@Valid @RequestBody NameplateContentCreateRequest request) {
        return nameplateContentService.create(request);
    }
}
