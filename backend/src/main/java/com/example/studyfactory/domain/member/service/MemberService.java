package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.service.BeverageService;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.MemberUpdateRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.service.SeatService;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BeverageService beverageService;
    private final MemberDeletionCleanupService memberDeletionCleanupService;
    private final SeatService seatService;

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        String searchName = toSearchName(name);
        Long targetBranchId = ManagerAccessPolicy.resolveOptionalAdminBranch(currentMember, branchId);

        return findMembers(searchName, targetBranchId)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> findPendingPreRegistrations(Long currentMemberId) {
        Member currentMember = findMember(currentMemberId);
        Long branchId = ManagerAccessPolicy.resolveOptionalAdminBranch(currentMember, null);

        return findPendingMembers(branchId)
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse findMe(Long currentMemberId) {
        return MemberResponse.from(findMember(currentMemberId));
    }

    @Transactional
    public MemberResponse update(Long currentMemberId, Long memberId, MemberUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member member = findMember(memberId);
        validateUpdateAccess(currentMember, member, request);
        if (seatAssignmentChanges(member, request)) {
            seatService.validateAssignment(member.getId(), request.branchId(), request.seatNumber());
        }
        member.update(
                request.branchId(),
                request.name().trim(),
                request.role(),
                request.seatNumber(),
                request.joinDate(),
                request.certificationId(),
                request.preparingCertifications()
        );

        return MemberResponse.from(member);
    }

    @Transactional
    public void delete(Long currentMemberId, Long memberId) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member member = findMemberForUpdate(memberId);
        validateDeleteAccess(currentMember, member);
        memberDeletionCleanupService.cleanup(member.getId());
        memberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public List<PreRegistrationVerifyResponse> verifyPreRegistration(PreRegistrationVerifyRequest request) {
        List<Member> members = memberRepository.findByNameAndReferenceInformationBranchIdAndPasswordIsNullOrderByIdAsc(
                request.name().trim(),
                request.branchId()
        );
        if (members.isEmpty()) {
            throw MemberException.preRegistrationNotFound();
        }

        return members.stream()
                .map(member -> PreRegistrationVerifyResponse.from(member, beverageService.findItems(member.getId())))
                .toList();
    }

    @Transactional
    public MemberSignupResponse signup(MemberSignupRequest request) {
        Member member = findMember(request.memberId());
        validateNotSignedUp(member);
        validateDuplicatedPassword(member, request.password());
        member.signup(request.password());

        return MemberSignupResponse.from(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberException::memberNotFound);
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

    private List<Member> findPendingMembers(Long branchId) {
        if (branchId != null) {
            return memberRepository.findByReferenceInformationBranchIdAndRoleAndPasswordIsNullOrderByIdAsc(
                    branchId,
                    MemberRole.MEMBER
            );
        }

        return memberRepository.findPendingPreRegistrations(Sort.by(Sort.Direction.ASC, "id"));
    }

    private void validateUpdateAccess(Member operator, Member target, MemberUpdateRequest request) {
        if (operator.getRole() == MemberRole.ADMIN) {
            return;
        }

        ManagerAccessPolicy.validateMemberTarget(operator, target);
        ManagerAccessPolicy.validateBranch(operator, request.branchId());
        if (request.role() != MemberRole.MEMBER) {
            throw MemberException.forbidden();
        }
    }

    private void validateDeleteAccess(Member operator, Member target) {
        if (operator.getRole() != MemberRole.ADMIN) {
            ManagerAccessPolicy.validateMemberTarget(operator, target);
        }
    }

    private boolean seatAssignmentChanges(Member member, MemberUpdateRequest request) {
        return !Objects.equals(member.getBranchId(), request.branchId())
                || !Objects.equals(member.getSeatNumber(), request.seatNumber());
    }

    private void validateNotSignedUp(Member member) {
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }
    }

    private void validateDuplicatedPassword(Member member, String password) {
        if (memberRepository.existsByNameAndBranchIdAndPassword(member.getName(), member.getBranchId(), password)) {
            throw MemberException.alreadySignedUp();
        }
    }

}
