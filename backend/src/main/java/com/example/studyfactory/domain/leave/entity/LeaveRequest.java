package com.example.studyfactory.domain.leave.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
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
@Table(name = "leave_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private LocalDate leaveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveType leaveType;

    public LeaveRequest(Long memberId, Long branchId, LocalDate leaveDate, LeaveType leaveType) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.leaveDate = leaveDate;
        this.leaveType = leaveType;
    }
}
