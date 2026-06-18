package com.example.studyfactory.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkInformation {

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private LocalDate joinDate;

    public WorkInformation(int seatNumber, LocalDate joinDate) {
        this.seatNumber = seatNumber;
        this.joinDate = joinDate;
    }
}
