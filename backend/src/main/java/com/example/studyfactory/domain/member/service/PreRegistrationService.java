package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreRegistrationService {

    private final MemberRepository memberRepository;
    private final BeveragePreferenceRepository beveragePreferenceRepository;
    private final BranchRepository branchRepository;
    private final NameplateContentRepository nameplateContentRepository;

    @Transactional
    public PreRegistrationResponse create(PreRegistrationCreateRequest request) {
        validateRequest(request);
        Long nameplateContentId = getNameplateContentId(request);
        Member member = new Member(
                request.branchId(),
                request.name().trim(),
                null,
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                nameplateContentId,
                request.memberNote()
        );
        Member savedMember = memberRepository.save(member);
        BeveragePreference beveragePreference = beveragePreferenceRepository.save(new BeveragePreference(
                savedMember.getId(),
                savedMember.getBranchId(),
                request.drinkSetting(),
                request.drinkNote()
        ));

        return PreRegistrationResponse.from(savedMember, beveragePreference);
    }

    private void validateRequest(PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
    }

    private Long getNameplateContentId(PreRegistrationCreateRequest request) {
        if (request.nameplateContent() == null || request.nameplateContent().isBlank()) {
            return null;
        }

        return saveOrGetNameplateContentId(request.nameplateContent().trim());
    }

    private Long saveOrGetNameplateContentId(String content) {
        return nameplateContentRepository.findByContent(content)
                .map(NameplateContent::getId)
                .orElseGet(() -> nameplateContentRepository.save(new NameplateContent(content)).getId());
    }
}
