package com.example.studyfactory.domain.beverage.dto;

/** A single drink selection.  Items are intentionally independent so the same drink can be ordered twice. */
public record BeverageItemRequest(
        String name,
        String note
) {
}
