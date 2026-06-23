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

    @Column
    private Integer seatNumber;

    @Column(nullable = false)
    private LocalDate joinDate;

    public WorkInformation(Integer seatNumber, LocalDate joinDate) {
        this.seatNumber = seatNumber;
        this.joinDate = joinDate;
    }

    public void update(Integer seatNumber, LocalDate joinDate) {
        this.seatNumber = seatNumber;
        this.joinDate = joinDate;
    }
}
