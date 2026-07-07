package com.example.studyfactory.domain.suggestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuggestionReferenceInformation {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "resolved_by_member_id")
    private Long resolvedByMemberId;

    public SuggestionReferenceInformation(Long memberId, Long branchId, Long resolvedByMemberId) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.resolvedByMemberId = resolvedByMemberId;
    }

    public void resolveBy(Long memberId) {
        this.resolvedByMemberId = memberId;
    }

    public void clearResolver() {
        this.resolvedByMemberId = null;
    }
}
