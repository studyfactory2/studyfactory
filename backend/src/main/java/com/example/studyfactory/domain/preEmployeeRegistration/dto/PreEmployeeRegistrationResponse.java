package com.example.studyfactory.domain.preEmployeeRegistration.dto;

import com.example.studyfactory.domain.preEmployeeRegistration.entity.PreEmployeeRegistration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PreEmployeeRegistrationResponse(
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

    public static PreEmployeeRegistrationResponse from(PreEmployeeRegistration registration) {
        return new PreEmployeeRegistrationResponse(
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
