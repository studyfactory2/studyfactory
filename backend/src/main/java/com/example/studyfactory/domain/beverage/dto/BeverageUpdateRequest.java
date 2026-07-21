package com.example.studyfactory.domain.beverage.dto;

import java.util.Map;

public record BeverageUpdateRequest(
        String drinkSetting,
        Map<String, String> drinkNotes,
        String drinkNote
) {

    public BeverageUpdateRequest(String drinkSetting, String drinkNote) {
        this(drinkSetting, null, drinkNote);
    }
}
