package com.example.studyfactory.domain.member.dto;

import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public record PreRegistrationResponse(
        Long id,
        Long branchId,
        String name,
        MemberRole role,
        Integer seatNumber,
        LocalDate expectedJoinDate,
        Long certificationId,
        String drinkSetting,
        Map<String, String> drinkNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String registrationCode,
        LocalDateTime registrationCodeExpiresAt
) {

    public static PreRegistrationResponse from(Member member, List<BeverageItem> items) {
        return new PreRegistrationResponse(
                member.getId(),
                member.getBranchId(),
                member.getName(),
                member.getRole(),
                member.getSeatNumber(),
                member.getJoinDate(),
                member.getCertificationId(),
                toDrinkText(items),
                toDrinkNotes(items),
                member.getCreatedAt(),
                member.getUpdatedAt(),
                null,
                null
        );
    }

    public static PreRegistrationResponse from(
            Member member,
            List<BeverageItem> items,
            String registrationCode,
            LocalDateTime registrationCodeExpiresAt
    ) {
        PreRegistrationResponse response = from(member, items);
        return new PreRegistrationResponse(
                response.id(),
                response.branchId(),
                response.name(),
                response.role(),
                response.seatNumber(),
                response.expectedJoinDate(),
                response.certificationId(),
                response.drinkSetting(),
                response.drinkNotes(),
                response.createdAt(),
                response.updatedAt(),
                registrationCode,
                registrationCodeExpiresAt
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
