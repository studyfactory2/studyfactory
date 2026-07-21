package com.example.studyfactory.domain.beverage.dto;

import java.util.Map;

public record BeverageRequest(
        String drinkSetting,
        Map<String, String> drinkNotes,
        String drinkNote
) {

    public BeverageRequest(String drinkSetting, String drinkNote) {
        this(drinkSetting, null, drinkNote);
    }
}
