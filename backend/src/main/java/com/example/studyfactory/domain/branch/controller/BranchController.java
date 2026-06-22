package com.example.studyfactory.domain.branch.controller;

import com.example.studyfactory.domain.branch.dto.BranchCreateRequest;
import com.example.studyfactory.domain.branch.dto.BranchResponse;
import com.example.studyfactory.domain.branch.service.BranchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public List<BranchResponse> findAll() {
        return branchService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@Valid @RequestBody BranchCreateRequest request) {
        return branchService.create(request);
    }
}
