package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import java.time.LocalDate;

public record PreRegistrationVerifyResponse(
        Long preRegistrationId,
        Long branchId,
        Long employeeTypeId,
        String name,
        int seatNumber,
        LocalDate expectedJoinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote
) {

    public static PreRegistrationVerifyResponse from(PreRegistration preRegistration) {
        return new PreRegistrationVerifyResponse(
                preRegistration.getId(),
                preRegistration.getBranchId(),
                preRegistration.getEmployeeTypeId(),
                preRegistration.getName(),
                preRegistration.getSeatNumber(),
                preRegistration.getExpectedJoinDate(),
                preRegistration.getNameplateContentId(),
                preRegistration.getDrinkSetting(),
                preRegistration.getDrinkNote(),
                preRegistration.getMemberNote()
        );
    }
}
