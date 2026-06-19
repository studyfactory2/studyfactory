package com.example.studyfactory.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubInformation {

    @Column(columnDefinition = "text")
    private String drinkSetting;

    @Column(columnDefinition = "text")
    private String drinkNote;

    @Column(columnDefinition = "text")
    private String memberNote;

    public SubInformation(String drinkSetting, String drinkNote, String memberNote) {
        this.drinkSetting = drinkSetting;
        this.drinkNote = drinkNote;
        this.memberNote = memberNote;
    }

    public void updateDrink(String drinkSetting, String drinkNote) {
        this.drinkSetting = drinkSetting;
        this.drinkNote = drinkNote;
    }
}
