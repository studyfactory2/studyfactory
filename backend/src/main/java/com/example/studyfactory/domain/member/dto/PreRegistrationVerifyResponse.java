package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.member.entity.Member;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public record PreRegistrationVerifyResponse(
        Long memberId,
        Long branchId,
        String name,
        Integer seatNumber,
        LocalDate expectedJoinDate,
        Long certificationId,
        String drinkSetting,
        Map<String, String> drinkNotes
) {

    public static PreRegistrationVerifyResponse from(Member member, List<BeverageItem> items) {
        return new PreRegistrationVerifyResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getCertificationId(),
                toDrinkText(items),
                toDrinkNotes(items)
        );
    }

    private static String toDrinkText(List<BeverageItem> items) {
        return items.stream().map(BeverageItem::getName).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private static Map<String, String> toDrinkNotes(List<BeverageItem> items) {
        return items.stream()
                .filter(item -> item.getNote() != null && !item.getNote().isBlank())
                .collect(java.util.stream.Collectors.toMap(BeverageItem::getName, BeverageItem::getNote, (left, right) -> right, java.util.LinkedHashMap::new));
    }

}
