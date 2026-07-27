package com.example.studyfactory.domain.beverage.dto;

import java.util.List;
import java.util.Map;

public record BeverageUpdateRequest(
        String drinkSetting,
        Map<String, String> drinkNotes,
        String drinkNote,
        List<BeverageItemRequest> items
) {

    public BeverageUpdateRequest(String drinkSetting, String drinkNote) {
        this(drinkSetting, null, drinkNote, null);
    }

    public BeverageUpdateRequest(String drinkSetting, Map<String, String> drinkNotes, String drinkNote) {
        this(drinkSetting, drinkNotes, drinkNote, null);
    }
}
