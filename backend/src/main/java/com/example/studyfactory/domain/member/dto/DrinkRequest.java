package com.example.studyfactory.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record DrinkRequest(
        @NotBlank(message = "음료 설정은 필수입니다.")
        String drinkSetting,

        @NotBlank(message = "음료 참고사항은 필수입니다.")
        String drinkNote
) {
}
