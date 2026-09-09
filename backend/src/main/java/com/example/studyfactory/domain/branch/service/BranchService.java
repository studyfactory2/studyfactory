package com.example.studyfactory.domain.branch.service;

import com.example.studyfactory.domain.branch.dto.BranchCreateRequest;
import com.example.studyfactory.domain.branch.dto.BranchResponse;
import com.example.studyfactory.domain.branch.entity.Branch;
import com.example.studyfactory.domain.branch.exception.BranchException;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<BranchResponse> findAll() {
        return branchRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(BranchResponse::from)
                .toList();
    }

    @Transactional
    public BranchResponse create(Long currentMemberId, BranchCreateRequest request) {
        Member operator = memberRepository.findById(currentMemberId)
                .orElseThrow(MemberException::memberNotFound);
        ManagerAccessPolicy.validateAdmin(operator);

        String name = request.name().trim();
        if (branchRepository.existsByName(name)) {
            throw BranchException.duplicatedName();
        }

        Branch branch = request.toEntity();

        return BranchResponse.from(branchRepository.save(branch));
    }
}
