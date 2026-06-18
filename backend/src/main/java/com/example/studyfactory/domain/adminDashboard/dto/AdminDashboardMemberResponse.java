package com.example.studyfactory.domain.adminDashboard.dto;

import com.example.studyfactory.domain.member.entity.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminDashboardMemberResponse(
        Long id,
        Long branchId,
        Long employeeTypeId,
        String name,
        int seatNumber,
        LocalDate joinDate,
        Long nameplateContentId,
        String drinkSetting,
        String drinkNote,
        String memberNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminDashboardMemberResponse from(Member member) {
        return new AdminDashboardMemberResponse(
                member.getId(),
                member.getReferenceInformation().getBranchId(),
                member.getReferenceInformation().getEmployeeTypeId(),
                member.getName(),
                member.getWorkInformation().getSeatNumber(),
                member.getWorkInformation().getJoinDate(),
                member.getReferenceInformation().getNameplateContentId(),
                member.getSubInformation().getDrinkSetting(),
                member.getSubInformation().getDrinkNote(),
                member.getSubInformation().getMemberNote(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
