package com.example.studyfactory.domain.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record FixedLeaveBatchCreateRequest(@NotNull @Min(1) Long memberId,
                                          @NotEmpty @Size(max = 31) List<@NotNull LocalDate> dates,
                                          @NotEmpty @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> slots,
                                          @NotBlank @Size(max = 40) String reason) {}
