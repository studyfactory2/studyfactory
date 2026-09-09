package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import java.util.Objects;

/**
 * Shared authorization rules for branch-scoped manager operations.
 *
 * <p>ADMIN may explicitly operate on any branch. STAFF is always pinned to
 * their own branch, even when a caller supplies a different branch id. MEMBER
 * may not use manager endpoints.</p>
 */
public final class ManagerAccessPolicy {

    private ManagerAccessPolicy() {
    }

    public static void validateManager(Member operator) {
        if (operator.getRole() != MemberRole.ADMIN && operator.getRole() != MemberRole.STAFF) {
            throw MemberException.forbidden();
        }
    }

    /**
     * Resolves an endpoint whose missing branch means the operator's branch.
     */
    public static Long resolveRequiredBranch(Member operator, Long requestedBranchId) {
        validateManager(operator);
        if (requestedBranchId == null) {
            return operator.getBranchId();
        }
        validateBranch(operator, requestedBranchId);
        return requestedBranchId;
    }

    /**
     * Resolves an endpoint where ADMIN may omit the branch to request all
     * branches. STAFF still resolves to exactly their own branch.
     */
    public static Long resolveOptionalAdminBranch(Member operator, Long requestedBranchId) {
        validateManager(operator);
        if (operator.getRole() == MemberRole.ADMIN) {
            return requestedBranchId;
        }
        validateBranch(operator, requestedBranchId == null ? operator.getBranchId() : requestedBranchId);
        return operator.getBranchId();
    }

    public static void validateBranch(Member operator, Long targetBranchId) {
        validateManager(operator);
        if (operator.getRole() != MemberRole.ADMIN
                && !Objects.equals(operator.getBranchId(), targetBranchId)) {
            throw MemberException.forbidden();
        }
    }

    public static void validateMemberTarget(Member operator, Member targetMember) {
        validateManager(operator);
        if (targetMember.getRole() != MemberRole.MEMBER) {
            throw MemberException.forbidden();
        }
        validateManagerTarget(operator, targetMember);
    }

    public static void validateManagerTarget(Member operator, Member targetMember) {
        validateManager(operator);
        validateBranch(operator, targetMember.getBranchId());
    }
}
