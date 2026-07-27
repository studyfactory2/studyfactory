package com.example.studyfactory.domain.beverage.dto;

import java.util.List;
import java.util.Map;

public record BeverageRequest(
        String drinkSetting,
        Map<String, String> drinkNotes,
        String drinkNote,
        List<BeverageItemRequest> items
) {

    public BeverageRequest(String drinkSetting, String drinkNote) {
        this(drinkSetting, null, drinkNote, null);
    }

    public BeverageRequest(String drinkSetting, Map<String, String> drinkNotes, String drinkNote) {
        this(drinkSetting, drinkNotes, drinkNote, null);
    }
}
