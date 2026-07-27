package com.example.studyfactory.domain.beverage.dto;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;

public record BeverageItemResponse(
        Long id,
        String name,
        String note
) {
    public static BeverageItemResponse from(BeverageItem item) {
        return new BeverageItemResponse(item.getId(), item.getName(), item.getNote());
    }
}
