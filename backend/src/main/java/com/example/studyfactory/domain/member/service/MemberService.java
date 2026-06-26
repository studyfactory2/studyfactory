package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.MemberUpdateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BeverageService beverageService;

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll(String name, Long branchId) {
        String searchName = toSearchName(name);

        return findMembers(searchName, branchId)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> findPendingPreRegistrations() {
        return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse update(Long currentMemberId, Long memberId, MemberUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member member = findMember(memberId);
        member.update(
                request.branchId(),
                request.name().trim(),
                request.role(),
                request.seatNumber(),
                request.joinDate(),
                request.certificationId(),
                request.memberNote(),
                request.preparingCertifications()
        );

        return MemberResponse.from(member);
    }

    @Transactional
    public void delete(Long currentMemberId, Long memberId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member member = findMember(memberId);
        beverageService.deleteAllByMemberId(member.getId());
        memberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public PreRegistrationVerifyResponse verifyPreRegistration(PreRegistrationVerifyRequest request) {
        Member member = findPreRegisteredMember(request.name().trim(), request.branchId());
        BeveragePreference beveragePreference = beverageService.findLatestPreference(member);

        return PreRegistrationVerifyResponse.from(member, beveragePreference);
    }

    @Transactional
    public MemberSignupResponse signup(MemberSignupRequest request) {
        Member member = findPreRegisteredMember(request.name().trim(), request.branchId());
        validateNotSignedUp(member);
        member.signup(request.password());

        return MemberSignupResponse.from(member);
    }

    private Member findPreRegisteredMember(String name, Long branchId) {
        Member member = memberRepository.findByNameAndBranchId(name, branchId)
                .orElseThrow(MemberException::preRegistrationNotFound);
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }

        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }

    private List<Member> findMembers(String name, Long branchId) {
        if (name != null && branchId != null) {
            return memberRepository.findByNameContainingAndReferenceInformationBranchIdOrderByIdAsc(name, branchId);
        }

        if (name != null) {
            return memberRepository.findByNameContainingOrderByIdAsc(name);
        }

        if (branchId != null) {
            return memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(branchId);
        }

        return memberRepository.findAllByOrderByIdAsc();
    }

    private void validateNotSignedUp(Member member) {
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }
}
