package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberSignupResponse(
        Long id,
        Long branchId,
        String name,
        MemberRole role,
        int seatNumber,
        LocalDate joinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberSignupResponse from(Member member) {
        return new MemberSignupResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getRole(),
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
