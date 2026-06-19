package com.example.studyfactory.domain.sideDish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SideDishMealInformation {

    @Column(nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealType mealType;

    public SideDishMealInformation(LocalDate mealDate, MealType mealType) {
        this.mealDate = mealDate;
        this.mealType = mealType;
    }
}
