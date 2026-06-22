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

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Embedded
    private WorkInformation workInformation;

    @Column(columnDefinition = "text")
    private String memberNote;

    public Member(Long branchId, String name, String password, int seatNumber, LocalDate joinDate, Long nameplateContentId,
                  String memberNote) {
        this(branchId, name, password, MemberRole.MEMBER, seatNumber, joinDate, nameplateContentId, memberNote);
    }

    public Member(Long branchId, String name, String password, MemberRole role, int seatNumber, LocalDate joinDate,
                  Long nameplateContentId, String memberNote) {
        this(
                null,
                new ReferenceInformation(branchId, nameplateContentId),
                name,
                password,
                role,
                new WorkInformation(seatNumber, joinDate),
                memberNote
        );
    }

    private Member(
            Long id,
            ReferenceInformation referenceInformation,
            String name,
            String password,
            MemberRole role,
            WorkInformation workInformation,
            String memberNote
    ) {
        this.id = id;
        this.referenceInformation = referenceInformation;
        this.name = name;
        this.password = password;
        this.role = role;
        this.workInformation = workInformation;
        this.memberNote = memberNote;
    }

    public Long getBranchId() {
        return referenceInformation.getBranchId();
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

    public void signup(String password) {
        this.password = password;
    }

    public boolean hasAllPermissions() {
        return role.hasAllPermissions();
    }
}
