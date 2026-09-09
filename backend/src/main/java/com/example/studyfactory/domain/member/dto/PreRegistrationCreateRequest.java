package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record PreRegistrationCreateRequest(
        @NotNull(message = "지점은 필수입니다.")
        Long branchId,
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,
        @NotNull(message = "회원 권한은 필수입니다.")
        MemberRole role,
        @Positive(message = "좌석번호는 1 이상이어야 합니다.")
        Integer seatNumber,
        LocalDate expectedJoinDate,
        @Size(max = 100, message = "자격증은 100자 이하여야 합니다.")
        String certification,
        String drinkSetting,
        Map<String, String> drinkNotes,
        String drinkNote
) {

    public PreRegistrationCreateRequest(Long branchId, String name, MemberRole role, Integer seatNumber,
                                        LocalDate expectedJoinDate, String certification, String drinkSetting, String drinkNote) {
        this(branchId, name, role, seatNumber, expectedJoinDate, certification, drinkSetting, null, drinkNote);
    }
}
