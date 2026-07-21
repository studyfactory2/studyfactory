package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.member.entity.MemberRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MemberUpdateRequest(
        @NotNull(message = "지점을 선택해주세요.")
        Long branchId,

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotNull(message = "사원 구분을 선택해주세요.")
        MemberRole role,

        Integer seatNumber,

        @NotNull(message = "입사일을 입력해주세요.")
        LocalDate joinDate,

        Long certificationId,

        String preparingCertifications
) {
}
