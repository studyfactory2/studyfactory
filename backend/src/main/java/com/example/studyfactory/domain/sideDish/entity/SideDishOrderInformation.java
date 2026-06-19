package com.example.studyfactory.domain.sideDish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SideDishOrderInformation {

    @Column(nullable = false, columnDefinition = "text")
    private String items;

    @Column(nullable = false)
    private int totalPrice;

    public SideDishOrderInformation(String items, int totalPrice) {
        this.items = items;
        this.totalPrice = totalPrice;
    }
}
