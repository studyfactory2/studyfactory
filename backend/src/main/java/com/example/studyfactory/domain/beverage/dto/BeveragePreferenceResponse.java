package com.example.studyfactory.domain.beverage.dto;

import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import java.time.LocalDateTime;

public record BeveragePreferenceResponse(
        Long id,
        Long memberId,
        Long branchId,
        String drinks,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BeveragePreferenceResponse from(BeveragePreference beveragePreference) {
        return new BeveragePreferenceResponse(
                beveragePreference.getId(),
                beveragePreference.getMemberId(),
                beveragePreference.getBranchId(),
                beveragePreference.getDrinks(),
                beveragePreference.getNotes(),
                beveragePreference.getCreatedAt(),
                beveragePreference.getUpdatedAt()
        );
    }
}
