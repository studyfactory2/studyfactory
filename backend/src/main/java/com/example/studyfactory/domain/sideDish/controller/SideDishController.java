package com.example.studyfactory.domain.sideDish.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.sideDish.dto.SideDishCreateRequest;
import com.example.studyfactory.domain.sideDish.dto.SideDishResponse;
import com.example.studyfactory.domain.sideDish.service.SideDishService;
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
@RequestMapping("/api/side-dishes")
public class SideDishController {

    private final SideDishService sideDishService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SideDishResponse create(@CurrentMember Long memberId, @Valid @RequestBody SideDishCreateRequest request) {
        return sideDishService.create(memberId, request);
    }
}
