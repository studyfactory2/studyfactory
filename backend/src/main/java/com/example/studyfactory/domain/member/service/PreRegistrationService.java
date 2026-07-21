package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
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
    private final BeverageService beverageService;
    private final MemberDeletionCleanupService memberDeletionCleanupService;
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
                certificationId
        );
        Member savedMember = memberRepository.save(member);
        List<BeverageItem> beverageItems = request.drinkNotes() == null
                ? beverageService.createPreference(savedMember.getId(), request.drinkSetting(), request.drinkNote())
                : beverageService.createPreference(savedMember.getId(), request.drinkSetting(), request.drinkNotes());

        return PreRegistrationResponse.from(savedMember, beverageItems);
    }

    @Transactional(readOnly = true)
    public List<PreRegistrationResponse> findPending() {
        return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(member -> PreRegistrationResponse.from(member, beverageService.findItems(member.getId())))
                .toList();
    }

    @Transactional
    public PreRegistrationResponse update(Long memberId, PreRegistrationCreateRequest request) {
        Member member = findPendingMember(memberId);
        validateUpdateRequest(member.getId(), request);
        Long certificationId = getCertificationId(request);
        member.updatePreRegistration(
                request.branchId(),
                request.name().trim(),
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                certificationId
        );
        List<BeverageItem> beverageItems = request.drinkNotes() == null
                ? beverageService.updatePreference(member, request.drinkSetting(), request.drinkNote())
                : beverageService.updatePreference(member, request.drinkSetting(), request.drinkNotes());

        return PreRegistrationResponse.from(member, beverageItems);
    }

    @Transactional
    public void delete(Long memberId) {
        Member member = findPendingMember(memberId);
        memberDeletionCleanupService.cleanup(member.getId());
        memberRepository.delete(member);
    }

    private void validateRequest(PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
        validateSeatAvailable(request.branchId(), request.seatNumber());
    }

    private void validateUpdateRequest(Long memberId, PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
        validateSeatAvailable(memberId, request.branchId(), request.seatNumber());
    }

    private void validateSeatAvailable(Long branchId, Integer seatNumber) {
        if (seatNumber == null) {
            return;
        }
        if (memberRepository.existsAssignedSeat(branchId, seatNumber)) {
            throw PreRegistrationException.seatAlreadyAssigned();
        }
    }

    private void validateSeatAvailable(Long memberId, Long branchId, Integer seatNumber) {
        if (seatNumber == null) {
            return;
        }
        if (memberRepository.existsAssignedSeat(branchId, seatNumber, memberId)) {
            throw PreRegistrationException.seatAlreadyAssigned();
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

}
