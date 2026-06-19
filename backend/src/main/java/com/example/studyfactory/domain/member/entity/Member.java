package com.example.studyfactory.domain.member.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private ReferenceInformation referenceInformation;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Embedded
    private WorkInformation workInformation;

    @Embedded
    private SubInformation subInformation;

    public Member(Long branchId, Long employeeTypeId, String name, String password, int seatNumber, LocalDate joinDate,
                  Long nameplateContentId, String drinkSetting, String drinkNote, String memberNote) {
        this(
                null,
                new ReferenceInformation(branchId, employeeTypeId, nameplateContentId),
                name,
                password,
                MemberRole.MEMBER,
                new WorkInformation(seatNumber, joinDate),
                new SubInformation(drinkSetting, drinkNote, memberNote)
        );
    }

    private Member(
            Long id,
            ReferenceInformation referenceInformation,
            String name,
            String password,
            MemberRole role,
            WorkInformation workInformation,
            SubInformation subInformation
    ) {
        this.id = id;
        this.referenceInformation = referenceInformation;
        this.name = name;
        this.password = password;
        this.role = role;
        this.workInformation = workInformation;
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

    public int getSeatNumber() {
        return workInformation.getSeatNumber();
    }

    public LocalDate getJoinDate() {
        return workInformation.getJoinDate();
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
