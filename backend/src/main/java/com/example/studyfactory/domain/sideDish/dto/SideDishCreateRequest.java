package com.example.studyfactory.domain.sideDish.dto;

import com.example.studyfactory.domain.sideDish.entity.MealType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record SideDishCreateRequest(
        @NotNull(message = "식사 종류는 필수입니다.")
        MealType mealType,

        @NotBlank(message = "메뉴명은 필수입니다.")
        String menuName,

        @Positive(message = "각 금액은 0보다 커야 합니다.")
        int itemPrice,

        @Positive(message = "총 가격은 0보다 커야 합니다.")
        int totalPrice,

        LocalDate mealDate
) {

    public SideDishCreateRequest(MealType mealType, String menuName, int itemPrice, int totalPrice) {
        this(mealType, menuName, itemPrice, totalPrice, null);
    }
}
