package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PreRegistrationResponse(
        Long id,
        Long branchId,
        String name,
        MemberRole role,
        int seatNumber,
        LocalDate expectedJoinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PreRegistrationResponse from(Member member, BeveragePreference beveragePreference) {
        return new PreRegistrationResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getRole(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getNameplateContentId(),
                beveragePreference.getDrinks(),
                beveragePreference.getNotes(),
                member.getMemberNote(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
