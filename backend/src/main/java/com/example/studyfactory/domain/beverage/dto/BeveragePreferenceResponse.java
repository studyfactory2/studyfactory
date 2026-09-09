package com.example.studyfactory.domain.beverage.dto;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.member.entity.Member;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record BeveragePreferenceResponse(
        Long id,
        Long memberId,
        Long branchId,
        String drinks,
        Map<String, String> drinkNotes,
        List<BeverageItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {

    public static BeveragePreferenceResponse from(
            Member member,
            List<BeverageItem> items,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BeveragePreferenceResponse(
                null,
                member.getId(),
                member.getBranchId(),
                toDrinkText(items),
                toDrinkNotes(items),
                items.stream().map(BeverageItemResponse::from).toList(),
                createdAt,
                updatedAt
        );
    }

    private static String toDrinkText(List<BeverageItem> items) {
        return items.stream().map(BeverageItem::getName).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private static Map<String, String> toDrinkNotes(List<BeverageItem> items) {
        Map<String, String> notes = new LinkedHashMap<>();
        items.forEach(item -> {
            if (item.getNote() != null && !item.getNote().isBlank()) {
                notes.put(item.getName(), item.getNote());
            }
        });
        return notes;
    }

}
