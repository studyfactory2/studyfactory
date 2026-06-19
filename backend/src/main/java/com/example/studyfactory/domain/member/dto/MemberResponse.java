package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        Long branchId,
        Long employeeTypeId,
        String name,
        int seatNumber,
        LocalDate joinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getBranchId(),
                member.getEmployeeTypeId(),
                member.getName(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getNameplateContentId(),
                member.getDrinkSetting(),
                member.getDrinkNote(),
                member.getMemberNote(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
