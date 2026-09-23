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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_members_branch_name",
                columnNames = {"branch_id", "name"}
        )
)
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
    private String preparingCertifications;

    @Column(length = 64)
    private String registrationCodeHash;

    @Column
    private LocalDateTime registrationCodeExpiresAt;

    @Column
    private Integer registrationCodeFailedAttempts = 0;

    public Member(Long branchId, String name, String password, Integer seatNumber, LocalDate joinDate, Long certificationId) {
        this(branchId, name, password, MemberRole.MEMBER, seatNumber, joinDate, certificationId);
    }

    /** @deprecated 회원 참고사항은 더 이상 저장하지 않습니다. */
    @Deprecated
    public Member(Long branchId, String name, String password, Integer seatNumber, LocalDate joinDate, Long certificationId,
                  String ignoredMemberNote) {
        this(branchId, name, password, seatNumber, joinDate, certificationId);
    }

    public Member(Long branchId, String name, String password, MemberRole role, Integer seatNumber, LocalDate joinDate,
                  Long certificationId) {
        this(
                null,
                new ReferenceInformation(branchId, certificationId),
                name,
                password,
                role,
                new WorkInformation(seatNumber, joinDate),
                null
        );
    }

    /** @deprecated 회원 참고사항은 더 이상 저장하지 않습니다. */
    @Deprecated
    public Member(Long branchId, String name, String password, MemberRole role, Integer seatNumber, LocalDate joinDate,
                  Long certificationId, String ignoredMemberNote) {
        this(branchId, name, password, role, seatNumber, joinDate, certificationId);
    }

    private Member(
            Long id,
            ReferenceInformation referenceInformation,
            String name,
            String password,
            MemberRole role,
            WorkInformation workInformation,
            String preparingCertifications
    ) {
        this.id = id;
        this.referenceInformation = referenceInformation;
        this.name = name;
        this.password = password;
        this.role = role;
        this.workInformation = workInformation;
        this.preparingCertifications = preparingCertifications;
    }

    public Long getBranchId() {
        return referenceInformation.getBranchId();
    }

    public Long getCertificationId() {
        return referenceInformation.getCertificationId();
    }

    public Integer getSeatNumber() {
        return workInformation.getSeatNumber();
    }

    public LocalDate getJoinDate() {
        return workInformation.getJoinDate();
    }

    public void signup(String password) {
        this.password = password;
        clearRegistrationCode();
    }

    public void updatePreRegistration(
            Long branchId,
            String name,
            MemberRole role,
            Integer seatNumber,
            LocalDate joinDate,
            Long certificationId
    ) {
        clearRegistrationCode();
        referenceInformation.update(branchId, certificationId);
        this.name = name;
        this.role = role;
        workInformation.update(seatNumber, joinDate);
    }

    /** @deprecated 회원 참고사항은 더 이상 저장하지 않습니다. */
    @Deprecated
    public void updatePreRegistration(Long branchId, String name, MemberRole role, Integer seatNumber, LocalDate joinDate,
                                      Long certificationId, String ignoredMemberNote) {
        updatePreRegistration(branchId, name, role, seatNumber, joinDate, certificationId);
    }

    public void update(
            Long branchId,
            String name,
            MemberRole role,
            Integer seatNumber,
            LocalDate joinDate,
            Long certificationId,
            String preparingCertifications
    ) {
        if (password == null) {
            clearRegistrationCode();
        }
        referenceInformation.update(branchId, certificationId);
        this.name = name;
        this.role = role;
        workInformation.update(seatNumber, joinDate);
        this.preparingCertifications = preparingCertifications;
    }

    public void issueRegistrationCode(String codeHash, LocalDateTime expiresAt) {
        this.registrationCodeHash = Objects.requireNonNull(codeHash);
        this.registrationCodeExpiresAt = Objects.requireNonNull(expiresAt);
        this.registrationCodeFailedAttempts = 0;
    }

    public void recordRegistrationCodeFailure() {
        registrationCodeFailedAttempts = getRegistrationCodeFailedAttempts() + 1;
    }

    public void clearRegistrationCode() {
        registrationCodeHash = null;
        registrationCodeExpiresAt = null;
        registrationCodeFailedAttempts = 0;
    }

    public int getRegistrationCodeFailedAttempts() {
        return registrationCodeFailedAttempts == null ? 0 : registrationCodeFailedAttempts;
    }

    /** @deprecated 회원 참고사항은 더 이상 저장하지 않습니다. */
    @Deprecated
    public void update(Long branchId, String name, MemberRole role, Integer seatNumber, LocalDate joinDate,
                       Long certificationId, String ignoredMemberNote, String preparingCertifications) {
        update(branchId, name, role, seatNumber, joinDate, certificationId, preparingCertifications);
    }

    public void updateSeat(Integer seatNumber) {
        workInformation.updateSeat(seatNumber);
    }

    public boolean hasAllPermissions() {
        return role.hasAllPermissions();
    }
}
