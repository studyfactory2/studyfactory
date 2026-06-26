package com.example.studyfactory.domain.beverage.dto;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.LocalDateTime;

public record MemberBeverageResponse(
        Long memberId,
        Long branchId,
        String memberName,
        MemberRole role,
        Integer seatNumber,
        String drinks,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MemberBeverageResponse from(Member member, BeveragePreference beveragePreference) {
        return new MemberBeverageResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getRole(),
                member.getSeatNumber(),
                beveragePreference.getDrinks(),
                beveragePreference.getNotes(),
                beveragePreference.getCreatedAt(),
                beveragePreference.getUpdatedAt()
        );
    }
}
