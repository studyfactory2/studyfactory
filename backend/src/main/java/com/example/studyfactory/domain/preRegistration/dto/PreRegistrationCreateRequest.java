package com.example.studyfactory.domain.preRegistration.dto;

import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.entity.ReferenceInformation;
import com.example.studyfactory.domain.preRegistration.entity.SubInformation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PreRegistrationCreateRequest(
        @NotNull(message = "지점은 필수입니다.")
        Long branchId,
        @NotNull(message = "사원구분은 필수입니다.")
        Long employeeTypeId,
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @Positive(message = "좌석번호는 1 이상이어야 합니다.")
        int seatNumber,
        @NotNull(message = "입사예정일은 필수입니다.")
        LocalDate expectedJoinDate,
        @NotNull(message = "명패내용은 필수입니다.")
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote
) {
    public PreRegistration toEntity() {
        return new PreRegistration(
                new ReferenceInformation(branchId, employeeTypeId, nameplateContentId),
                name.trim(),
                seatNumber,
                expectedJoinDate,
                new SubInformation(drinkSetting, drinkNote, memberNote)
        );
    }
}
