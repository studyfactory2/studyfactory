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

    @Column(name = "employee_type_id", nullable = false)
    private Long employeeTypeId;

    @Column(name = "nameplate_content_id", nullable = false)
    private Long nameplateContentId;

    public ReferenceInformation(Long branchId, Long employeeTypeId, Long nameplateContentId) {
        this.branchId = branchId;
        this.employeeTypeId = employeeTypeId;
        this.nameplateContentId = nameplateContentId;
    }
}
