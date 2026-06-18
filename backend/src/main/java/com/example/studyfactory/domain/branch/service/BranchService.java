package com.example.studyfactory.domain.branch.service;

import com.example.studyfactory.domain.branch.dto.BranchCreateRequest;
import com.example.studyfactory.domain.branch.dto.BranchResponse;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.exception.BranchException;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public BranchResponse create(BranchCreateRequest request) {
        String name = request.name().trim();
        if (branchRepository.existsByName(name)) {
            throw BranchException.duplicatedName();
        }

        Branch branch = request.toEntity();

        return BranchResponse.from(branchRepository.save(branch));
    }
}
