package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreRegistrationService {

    private final MemberRepository memberRepository;
    private final BeveragePreferenceRepository beveragePreferenceRepository;
    private final BranchRepository branchRepository;
    private final CertificationRepository certificationRepository;

    @Transactional
    public PreRegistrationResponse create(PreRegistrationCreateRequest request) {
        validateRequest(request);
        Long certificationId = getCertificationId(request);
        Member member = new Member(
                request.branchId(),
                request.name().trim(),
                null,
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                certificationId,
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

    @Transactional(readOnly = true)
    public List<PreRegistrationResponse> findPending() {
        return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(member -> PreRegistrationResponse.from(member, findLatestBeveragePreference(member)))
                .toList();
    }

    @Transactional
    public PreRegistrationResponse update(Long memberId, PreRegistrationCreateRequest request) {
        validateRequest(request);
        Member member = findPendingMember(memberId);
        Long certificationId = getCertificationId(request);
        member.updatePreRegistration(
                request.branchId(),
                request.name().trim(),
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                certificationId,
                request.memberNote()
        );
        BeveragePreference beveragePreference = findOrCreateBeveragePreference(member);
        beveragePreference.update(request.drinkSetting(), request.drinkNote());

        return PreRegistrationResponse.from(member, beveragePreference);
    }

    @Transactional
    public void delete(Long memberId) {
        Member member = findPendingMember(memberId);
        beveragePreferenceRepository.deleteAll(beveragePreferenceRepository.findByMemberId(member.getId()));
        memberRepository.delete(member);
    }

    private void validateRequest(PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
    }

    private Long getCertificationId(PreRegistrationCreateRequest request) {
        if (request.certification() == null || request.certification().isBlank()) {
            return null;
        }

        return saveOrGetCertificationId(request.certification().trim());
    }

    private Long saveOrGetCertificationId(String content) {
        return certificationRepository.findByContent(content)
                .map(Certification::getId)
                .orElseGet(() -> certificationRepository.save(new Certification(content)).getId());
    }

    private Member findPendingMember(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::preRegistrationNotFound);
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }

        return member;
    }

    private BeveragePreference findLatestBeveragePreference(Member member) {
        return beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), "", null));
    }

    private BeveragePreference findOrCreateBeveragePreference(Member member) {
        return beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> beveragePreferenceRepository.save(new BeveragePreference(member.getId(), member.getBranchId(), "", null)));
    }
}
