package com.example.studyfactory.domain.branch.dto;

import com.example.studyfactory.domain.branch.entity.Branch;
import java.time.LocalDateTime;

public record BranchResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getCreatedAt(),
                branch.getUpdatedAt()
        );
    }
}
