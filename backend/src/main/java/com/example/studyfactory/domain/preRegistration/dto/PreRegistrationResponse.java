package com.example.studyfactory.domain.preRegistration.dto;

import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PreRegistrationResponse(
        Long id,
        Long branchId,
        Long employeeTypeId,
        String name,
        int seatNumber,
        LocalDate expectedJoinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PreRegistrationResponse from(PreRegistration registration) {
        return new PreRegistrationResponse(
                registration.getId(),
                registration.getBranchId(),
                registration.getEmployeeTypeId(),
                registration.getName(),
                registration.getSeatNumber(),
                registration.getExpectedJoinDate(),
                registration.getNameplateContentId(),
                registration.getDrinkSetting(),
                registration.getDrinkNote(),
                registration.getMemberNote(),
                registration.getCreatedAt(),
                registration.getUpdatedAt()
        );
    }
}
