package com.example.studyfactory.domain.sideDish.dto;

import com.example.studyfactory.domain.sideDish.entity.MealType;
import java.time.LocalDate;

public record DailySideDishResponse(
        Long id,
        Long memberId,
        Long branchId,
        String memberName,
        Integer seatNumber,
        LocalDate mealDate,
        MealType mealType,
        String items,
        int totalPrice
) {
}
