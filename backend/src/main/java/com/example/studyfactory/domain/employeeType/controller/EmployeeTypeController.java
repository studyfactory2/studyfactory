package com.example.studyfactory.domain.employeeType.controller;

import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeCreateRequest;
import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeResponse;
import com.example.studyfactory.domain.employeeType.service.EmployeeTypeService;
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
@RequestMapping("/api/employee-types")
public class EmployeeTypeController {

    private final EmployeeTypeService employeeTypeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeTypeResponse create(@Valid @RequestBody EmployeeTypeCreateRequest request) {
        return employeeTypeService.create(request);
    }
}
