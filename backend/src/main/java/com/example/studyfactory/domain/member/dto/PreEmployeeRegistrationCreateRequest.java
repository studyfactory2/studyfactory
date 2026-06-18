package com.example.studyfactory.domain.member.dto;

import java.time.LocalDate;

public record PreEmployeeRegistrationCreateRequest(
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
}
