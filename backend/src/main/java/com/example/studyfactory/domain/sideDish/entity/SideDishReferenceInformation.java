package com.example.studyfactory.domain.sideDish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SideDishReferenceInformation {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    public SideDishReferenceInformation(Long memberId, Long branchId) {
        this.memberId = memberId;
        this.branchId = branchId;
    }
}
