package com.example.studyfactory.domain.employeeType.service;

import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeCreateRequest;
import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeResponse;
import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import com.example.studyfactory.domain.employeeType.exception.EmployeeTypeException;
import com.example.studyfactory.domain.employeeType.repository.EmployeeTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeTypeService {

    private final EmployeeTypeRepository employeeTypeRepository;

    @Transactional
    public EmployeeTypeResponse create(EmployeeTypeCreateRequest request) {
        String name = request.name().trim();
        if (employeeTypeRepository.existsByName(name)) {
            throw EmployeeTypeException.duplicatedName();
        }

        EmployeeType employeeType = request.toEntity();

        return EmployeeTypeResponse.from(employeeTypeRepository.save(employeeType));
    }
}
