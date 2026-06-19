package com.example.studyfactory.domain.sideDish.dto;

import com.example.studyfactory.domain.sideDish.entity.MealType;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SideDishResponse(
        Long id,
        Long memberId,
        Long branchId,
        LocalDate mealDate,
        MealType mealType,
        String items,
        int totalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SideDishResponse from(SideDishRequest sideDishRequest) {
        return new SideDishResponse(
                sideDishRequest.getId(),
                sideDishRequest.getMemberId(),
                sideDishRequest.getBranchId(),
                sideDishRequest.getMealDate(),
                sideDishRequest.getMealType(),
                sideDishRequest.getItems(),
                sideDishRequest.getTotalPrice(),
                sideDishRequest.getCreatedAt(),
                sideDishRequest.getUpdatedAt()
        );
    }
}
