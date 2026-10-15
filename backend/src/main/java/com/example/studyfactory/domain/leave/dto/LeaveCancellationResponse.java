package com.example.studyfactory.domain.leave.dto;

/** Counts may overlap: cancelling a leave occurrence never removes its actual attendance record. */
public record LeaveCancellationResponse(Long memberId, Long branchId, int requestedPeriodCount,
                                        int cancelledPeriodCount, int preservedPresentPeriodCount) {}
