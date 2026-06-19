package com.example.studyfactory.domain.preRegistration.service;

import com.example.studyfactory.domain.preRegistration.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.preRegistration.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.exception.PreRegistrationException;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreRegistrationService {

    private final PreRegistrationRepository preRegistrationRepository;
    private final BranchRepository branchRepository;
    private final NameplateContentRepository nameplateContentRepository;

    @Transactional
    public PreRegistrationResponse create(PreRegistrationCreateRequest request) {
        validateRequest(request);
        PreRegistration preRegistration = request.toEntity();

        return PreRegistrationResponse.from(preRegistrationRepository.save(preRegistration));
    }

    private void validateRequest(PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
        if (!nameplateContentRepository.existsById(request.nameplateContentId())) {
            throw PreRegistrationException.invalidNameplateContent();
        }
    }
}
