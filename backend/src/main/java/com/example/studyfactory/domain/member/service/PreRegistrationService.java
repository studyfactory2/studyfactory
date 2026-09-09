package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import com.example.studyfactory.domain.room.service.SeatService;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import java.util.List;
import java.util.Objects;
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
    private final SeatService seatService;

    @Transactional
    public PreRegistrationResponse create(Long currentMemberId, PreRegistrationCreateRequest request) {
        Member operator = findOperator(currentMemberId);
        validateAssignableRole(operator, request.role());
        validateBranchScope(operator, request.branchId());
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
    public List<PreRegistrationResponse> findPending(Long currentMemberId) {
        Member operator = findOperator(currentMemberId);
        Long branchId = ManagerAccessPolicy.resolveOptionalAdminBranch(operator, null);

        return findPendingMembers(branchId)
                .stream()
                .map(member -> PreRegistrationResponse.from(member, beverageService.findItems(member.getId())))
                .toList();
    }

    @Transactional
    public PreRegistrationResponse update(
            Long currentMemberId,
            Long memberId,
            PreRegistrationCreateRequest request
    ) {
        Member operator = findOperator(currentMemberId);
        Member member = findPendingMember(memberId);
        validateTargetAccess(operator, member);
        // both the record's current branch and the requested one must be in scope,
        // so a branch-bound operator cannot move a member in or out of their branch
        validateBranchScope(operator, member.getBranchId());
        validateAssignableRole(operator, request.role());
        validateBranchScope(operator, request.branchId());
        validateUpdateRequest(member, request);
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
    public void delete(Long currentMemberId, Long memberId) {
        Member operator = findOperator(currentMemberId);
        Member member = findPendingMember(memberId);
        validateTargetAccess(operator, member);
        validateBranchScope(operator, member.getBranchId());
        memberDeletionCleanupService.cleanup(member.getId());
        memberRepository.delete(member);
    }

    /** Pre-registration is a manager operation: ADMIN or STAFF only. */
    private Member findOperator(Long currentMemberId) {
        Member operator = memberRepository.findById(currentMemberId)
                .orElseThrow(MemberException::memberNotFound);
        ManagerAccessPolicy.validateManager(operator);

        return operator;
    }

    /**
     * ADMIN may grant any role. STAFF may only pre-register plain members, so a
     * branch operator cannot mint another manager — or promote themselves by
     * pre-registering a second privileged account.
     */
    private void validateAssignableRole(Member operator, MemberRole requestedRole) {
        if (operator.getRole() != MemberRole.ADMIN && requestedRole != MemberRole.MEMBER) {
            throw MemberException.forbidden();
        }
    }

    /** ADMIN works across branches; STAFF is confined to their own. */
    private void validateBranchScope(Member operator, Long branchId) {
        ManagerAccessPolicy.validateBranch(operator, branchId);
    }

    private void validateTargetAccess(Member operator, Member member) {
        if (operator.getRole() != MemberRole.ADMIN) {
            ManagerAccessPolicy.validateMemberTarget(operator, member);
        }
    }

    private void validateRequest(PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
        seatService.validateAssignment(null, request.branchId(), request.seatNumber());
    }

    private void validateUpdateRequest(Member member, PreRegistrationCreateRequest request) {
        if (!branchRepository.existsById(request.branchId())) {
            throw PreRegistrationException.invalidBranch();
        }
        if (!Objects.equals(member.getBranchId(), request.branchId())
                || !Objects.equals(member.getSeatNumber(), request.seatNumber())) {
            seatService.validateAssignment(member.getId(), request.branchId(), request.seatNumber());
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

    private List<Member> findPendingMembers(Long branchId) {
        if (branchId != null) {
            return memberRepository.findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                    branchId,
                    MemberRole.MEMBER
            );
        }

        return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"));
    }

}
