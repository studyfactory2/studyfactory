package com.example.studyfactory.domain.preRegistration.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "pre_registrations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreRegistration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private ReferenceInformation referenceInformation;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private LocalDate expectedJoinDate;

    @Embedded
    private SubInformation subInformation;

    public PreRegistration(
            ReferenceInformation referenceInformation,
            String name,
            int seatNumber,
            LocalDate expectedJoinDate,
            SubInformation subInformation
    ) {
        this.referenceInformation = referenceInformation;
        this.name = name;
        this.seatNumber = seatNumber;
        this.expectedJoinDate = expectedJoinDate;
        this.subInformation = subInformation;
    }

    public Long getBranchId() {
        return referenceInformation.getBranchId();
    }

    public Long getEmployeeTypeId() {
        return referenceInformation.getEmployeeTypeId();
    }

    public Long getNameplateContentId() {
        return referenceInformation.getNameplateContentId();
    }

    public String getDrinkSetting() {
        return subInformation.getDrinkSetting();
    }

    public String getDrinkNote() {
        return subInformation.getDrinkNote();
    }

    public String getMemberNote() {
        return subInformation.getMemberNote();
    }
}
