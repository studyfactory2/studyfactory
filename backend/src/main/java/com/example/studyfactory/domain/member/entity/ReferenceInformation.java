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

    @Column(name = "nameplate_content_id")
    private Long nameplateContentId;

    public ReferenceInformation(Long branchId, Long nameplateContentId) {
        this.branchId = branchId;
        this.nameplateContentId = nameplateContentId;
    }

    public void update(Long branchId, Long nameplateContentId) {
        this.branchId = branchId;
        this.nameplateContentId = nameplateContentId;
    }
}
