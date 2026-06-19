package com.example.studyfactory.domain.branch.dto;

import com.example.studyfactory.domain.branch.entity.Branch;
import jakarta.validation.constraints.NotBlank;

public record BranchCreateRequest(
        @NotBlank(message = "지점 이름은 필수입니다.")
        String name,

        String address
) {

    public Branch toEntity() {
        return new Branch(name.trim(), trimToNull(address));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
