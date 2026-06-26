package com.example.studyfactory.domain.beverage.dto;

public record BeverageUpdateRequest(
        String drinkSetting,
        String drinkNote
) {
}
