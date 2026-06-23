package com.example.studyfactory.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceInformation {

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "certification_id")
    private Long certificationId;

    public ReferenceInformation(Long branchId, Long certificationId) {
        this.branchId = branchId;
        this.certificationId = certificationId;
    }

    public void update(Long branchId, Long certificationId) {
        this.branchId = branchId;
        this.certificationId = certificationId;
    }
}
