package com.example.studyfactory.domain.preRegistration.entity;

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

    @Column(name = "nameplate_content_id", nullable = false)
    private Long nameplateContentId;

    public ReferenceInformation(Long branchId, Long nameplateContentId) {
        this.branchId = branchId;
        this.nameplateContentId = nameplateContentId;
    }
}
