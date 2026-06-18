package com.example.studyfactory.domain.branch.dto;

import com.example.studyfactory.domain.branch.entity.Branch;
import jakarta.validation.constraints.NotBlank;

public record BranchCreateRequest(
        @NotBlank(message = "지점 이름은 필수입니다.")
        String name
) {

    public Branch toEntity() {
        return new Branch(name.trim());
    }
}
