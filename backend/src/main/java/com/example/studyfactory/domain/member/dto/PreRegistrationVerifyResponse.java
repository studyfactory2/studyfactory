package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.member.entity.Member;
import java.time.LocalDate;

public record PreRegistrationVerifyResponse(
        Long memberId,
        Long branchId,
        String name,
        Integer seatNumber,
        LocalDate expectedJoinDate,
        Long certificationId,
        String drinkSetting,
        String drinkNote
) {

    public static PreRegistrationVerifyResponse from(Member member, BeveragePreference beveragePreference) {
        return new PreRegistrationVerifyResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getCertificationId(),
                beveragePreference.getDrinks(),
                beveragePreference.getNotes()
        );
    }
}
