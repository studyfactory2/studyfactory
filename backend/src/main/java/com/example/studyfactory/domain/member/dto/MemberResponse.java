package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        Long branchId,
        String name,
        MemberRole role,
        Integer seatNumber,
        LocalDate joinDate,
        Long certificationId,
        String preparingCertifications,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getRole(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getCertificationId(),
                member.getPreparingCertifications(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
