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
                registration.getReferenceInformation().getBranchId(),
                registration.getReferenceInformation().getEmployeeTypeId(),
                registration.getName(),
                registration.getSeatNumber(),
                registration.getExpectedJoinDate(),
                registration.getReferenceInformation().getNameplateContentId(),
                registration.getSubInformation().getDrinkSetting(),
                registration.getSubInformation().getDrinkNote(),
                registration.getSubInformation().getMemberNote(),
                registration.getCreatedAt(),
                registration.getUpdatedAt()
        );
    }
}
