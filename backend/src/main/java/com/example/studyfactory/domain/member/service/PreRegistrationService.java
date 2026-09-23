package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.member.dto.RegistrationCodeIssueResponse;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.exception.PreRegistrationException;
import com.example.studyfactory.domain.room.service.SeatService;
import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final RegistrationCodeService registrationCodeService;

    @Transactional
    public PreRegistrationResponse create(Long currentMemberId, PreRegistrationCreateRequest request) {
        Member operator = findOperator(currentMemberId);
        validateAssignableRole(operator, request.role());
        validateBranchScope(operator, request.branchId());
        validateRequest(request);
        String normalizedName = request.name().trim();
        validateAvailableName(request.branchId(), normalizedName, null);
        Long certificationId = getCertificationId(operator, request);
        Member member = new Member(
                request.branchId(),
                normalizedName,
                null,
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                certificationId
        );
        Member savedMember;
        try {
            savedMember = memberRepository.save(member);
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            MemberConstraintViolationTranslator.rethrow(exception);
            throw exception;
        }
        List<BeverageItem> beverageItems = request.drinkNotes() == null
                ? beverageService.createPreference(savedMember.getId(), request.drinkSetting(), request.drinkNote())
                : beverageService.createPreference(savedMember.getId(), request.drinkSetting(), request.drinkNotes());
        RegistrationCodeService.IssuedCode issuedCode = issueCodeIfRequired(savedMember);

        return toResponse(savedMember, beverageItems, issuedCode);
    }

    @Transactional(readOnly = true)
    public List<PreRegistrationResponse> findPending(Long currentMemberId, Long requestedBranchId) {
        Member operator = findOperator(currentMemberId);
        Long branchId = ManagerAccessPolicy.resolveOptionalAdminBranch(operator, requestedBranchId);

        return findPendingMembers(operator, branchId)
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
        Member member = findPendingMemberForUpdate(operator, memberId);
        validateTargetAccess(operator, member);
        // both the record's current branch and the requested one must be in scope,
        // so a branch-bound operator cannot move a member in or out of their branch
        validateBranchScope(operator, member.getBranchId());
        validateAssignableRole(operator, request.role());
        validateBranchScope(operator, request.branchId());
        validateUpdateRequest(member, request);
        String normalizedName = request.name().trim();
        validateAvailableName(request.branchId(), normalizedName, member.getId());
        Long certificationId = getCertificationId(operator, request);
        member.updatePreRegistration(
                request.branchId(),
                normalizedName,
                request.role(),
                request.seatNumber(),
                request.expectedJoinDate(),
                certificationId
        );
        flushMemberChanges();
        List<BeverageItem> beverageItems = request.drinkNotes() == null
                ? beverageService.updatePreference(member, request.drinkSetting(), request.drinkNote())
                : beverageService.updatePreference(member, request.drinkSetting(), request.drinkNotes());
        RegistrationCodeService.IssuedCode issuedCode = issueCodeIfRequired(member);

        return toResponse(member, beverageItems, issuedCode);
    }

    @Transactional
    public RegistrationCodeIssueResponse reissueRegistrationCode(Long currentMemberId, Long memberId) {
        Member operator = findOperator(currentMemberId);
        ManagerAccessPolicy.validateAdmin(operator);
        Member member = findPendingMemberForUpdate(operator, memberId);
        if (!registrationCodeService.requiresCode(member.getRole())) {
            throw MemberException.invalidRegistrationCodeTarget();
        }
        validateUnambiguousLoginName(member);
        RegistrationCodeService.IssuedCode issuedCode = registrationCodeService.issue(member);
        return new RegistrationCodeIssueResponse(issuedCode.value(), issuedCode.expiresAt());
    }

    @Transactional
    public void delete(Long currentMemberId, Long memberId) {
        Member operator = findOperator(currentMemberId);
        Member member = findPendingMemberForUpdate(operator, memberId);
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

    private Long getCertificationId(Member operator, PreRegistrationCreateRequest request) {
        if (request.certification() == null || request.certification().isBlank()) {
            return null;
        }

        String content = request.certification().trim();
        return certificationRepository.findByContent(content)
                .map(Certification::getId)
                .orElseGet(() -> createCertification(operator, content));
    }

    private Long createCertification(Member operator, String content) {
        if (operator.getRole() != MemberRole.ADMIN) {
            throw PreRegistrationException.invalidCertification();
        }

        return certificationRepository.save(new Certification(content)).getId();
    }

    private Member findPendingMemberForUpdate(Member operator, Long memberId) {
        Member member = findManagedMemberForUpdate(operator, memberId);
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }

        return member;
    }

    private Member findManagedMemberForUpdate(Member operator, Long memberId) {
        if (operator.getRole() == MemberRole.ADMIN) {
            return memberRepository.findByIdForUpdate(memberId)
                    .orElseThrow(MemberException::preRegistrationNotFound);
        }

        return memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(memberId, operator.getBranchId())
                .orElseThrow(MemberException::preRegistrationNotFound);
    }

    private List<Member> findPendingMembers(Member operator, Long branchId) {
        if (branchId == null) {
            return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"));
        }
        if (operator.getRole() == MemberRole.ADMIN) {
            return memberRepository.findByReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(branchId);
        }

        return memberRepository.findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                branchId,
                MemberRole.MEMBER
        );
    }

    private RegistrationCodeService.IssuedCode issueCodeIfRequired(Member member) {
        if (!registrationCodeService.requiresCode(member.getRole())) {
            member.clearRegistrationCode();
            return null;
        }
        validateUnambiguousLoginName(member);
        return registrationCodeService.issue(member);
    }

    private void validateUnambiguousLoginName(Member member) {
        boolean duplicateNameExists = memberRepository
                .findAllByNameAndBranchId(member.getName(), member.getBranchId())
                .stream()
                .anyMatch(candidate -> !Objects.equals(candidate.getId(), member.getId()));
        if (duplicateNameExists) {
            throw MemberException.duplicateBranchName();
        }
    }

    private void validateAvailableName(Long branchId, String name, Long memberId) {
        boolean duplicateExists = memberId == null
                ? memberRepository.existsByNameAndBranchId(name, branchId)
                : memberRepository.existsByNameAndBranchIdExcludingMember(name, branchId, memberId);
        if (duplicateExists) {
            throw MemberException.duplicateBranchName();
        }
    }

    private void flushMemberChanges() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            MemberConstraintViolationTranslator.rethrow(exception);
        }
    }

    private PreRegistrationResponse toResponse(
            Member member,
            List<BeverageItem> beverageItems,
            RegistrationCodeService.IssuedCode issuedCode
    ) {
        if (issuedCode == null) {
            return PreRegistrationResponse.from(member, beverageItems);
        }
        return PreRegistrationResponse.from(
                member,
                beverageItems,
                issuedCode.value(),
                issuedCode.expiresAt()
        );
    }

}
