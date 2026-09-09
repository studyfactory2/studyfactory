package com.example.studyfactory.domain.branch.dto;

import com.example.studyfactory.domain.branch.entity.Branch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
        @NotBlank(message = "지점 이름은 필수입니다.")
        @Size(max = 50, message = "지점 이름은 50자 이하여야 합니다.")
        String name,

        @Size(max = 255, message = "지점 주소는 255자 이하여야 합니다.")
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
