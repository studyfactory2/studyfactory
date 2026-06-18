package com.example.studyfactory.domain.preEmployeeRegistration.service;

import com.example.studyfactory.domain.preEmployeeRegistration.entity.PreEmployeeRegistration;
import com.example.studyfactory.domain.preEmployeeRegistration.dto.PreEmployeeRegistrationCreateRequest;
import com.example.studyfactory.domain.preEmployeeRegistration.dto.PreEmployeeRegistrationResponse;
import com.example.studyfactory.domain.preEmployeeRegistration.repository.PreEmployeeRegistrationRepository;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.employeeType.repository.EmployeeTypeRepository;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PreEmployeeRegistrationService {

    private final PreEmployeeRegistrationRepository preEmployeeRegistrationRepository;
    private final BranchRepository branchRepository;
    private final EmployeeTypeRepository employeeTypeRepository;
    private final NameplateContentRepository nameplateContentRepository;

    @Transactional
    public PreEmployeeRegistrationResponse create(PreEmployeeRegistrationCreateRequest request) {
        validateRequest(request);

        PreEmployeeRegistration registration = new PreEmployeeRegistration(
                request.branchId(),
                request.employeeTypeId(),
                request.name().trim(),
                request.seatNumber(),
                request.expectedJoinDate(),
                request.nameplateContentId(),
                request.drinkSetting(),
                request.drinkNote(),
                request.memberNote()
        );

        return PreEmployeeRegistrationResponse.from(preEmployeeRegistrationRepository.save(registration));
    }

    private void validateRequest(PreEmployeeRegistrationCreateRequest request) {
        if (request.branchId() == null || !branchRepository.existsById(request.branchId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 지점입니다.");
        }
        if (request.employeeTypeId() == null || !employeeTypeRepository.existsById(request.employeeTypeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 사원구분입니다.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름은 필수입니다.");
        }
        if (preEmployeeRegistrationRepository.existsByName(request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 이름입니다.");
        }
        if (request.seatNumber() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "좌석번호는 1 이상이어야 합니다.");
        }
        if (request.expectedJoinDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입사예정일은 필수입니다.");
        }
        if (request.nameplateContentId() == null
                || !nameplateContentRepository.existsById(request.nameplateContentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 명패내용입니다.");
        }
    }
}
