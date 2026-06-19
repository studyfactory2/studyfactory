package com.example.studyfactory.domain.leave.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
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
@Table(name = "special_leaves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecialLeave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private LocalDate leaveDate;

    @Column(nullable = false, columnDefinition = "text")
    private String slots;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(columnDefinition = "text")
    private String customReason;

    @Column(nullable = false)
    private boolean recurring;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    public SpecialLeave(
            Long memberId,
            Long branchId,
            LocalDate leaveDate,
            String slots,
            String reason,
            String customReason,
            boolean recurring,
            Long createdByMemberId
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.leaveDate = leaveDate;
        this.slots = slots;
        this.reason = reason;
        this.customReason = customReason;
        this.recurring = recurring;
        this.createdByMemberId = createdByMemberId;
    }
}
