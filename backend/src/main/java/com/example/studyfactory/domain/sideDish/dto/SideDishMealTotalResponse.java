package com.example.studyfactory.domain.sideDish.dto;

import com.example.studyfactory.domain.sideDish.entity.MealType;

public record SideDishMealTotalResponse(
        MealType mealType,
        long totalPrice
) {
}
