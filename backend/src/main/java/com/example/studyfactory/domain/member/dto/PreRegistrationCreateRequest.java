package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PreRegistrationCreateRequest(
        @NotNull(message = "지점은 필수입니다.")
        Long branchId,
        @NotBlank(message = "이름은 필수입니다.")
        String name,
        @NotNull(message = "회원 권한은 필수입니다.")
        MemberRole role,
        @Positive(message = "좌석번호는 1 이상이어야 합니다.")
        Integer seatNumber,
        @NotNull(message = "입사예정일은 필수입니다.")
        LocalDate expectedJoinDate,
        String nameplateContent,
        String drinkSetting,
        String drinkNote,
        String memberNote
) {
}
