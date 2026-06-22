package com.example.studyfactory.domain.beverage.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "beverage_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeveragePreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, columnDefinition = "text")
    private String drinks;

    @Column(columnDefinition = "text")
    private String notes;

    public BeveragePreference(Long memberId, Long branchId, String drinks, String notes) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.drinks = drinks;
        this.notes = notes;
    }

    public void update(String drinks, String notes) {
        this.drinks = drinks;
        this.notes = notes;
    }

    public void addDrinks(String drinks, String notes) {
        this.drinks = mergeDrinks(drinks);
        this.notes = notes;
    }

    public boolean removeDrink(String drink) {
        String targetDrink = drink.trim();
        List<String> remainingDrinks = new ArrayList<>();
        boolean removed = false;

        for (String currentDrink : toDrinkItems()) {
            if (!removed && currentDrink.equals(targetDrink)) {
                removed = true;
                continue;
            }
            remainingDrinks.add(currentDrink);
        }

        if (removed) {
            this.drinks = String.join("\n", remainingDrinks);
        }

        return removed;
    }

    private String mergeDrinks(String newDrinks) {
        String trimmedDrinks = newDrinks.trim();
        if (drinks == null || drinks.isBlank()) {
            return trimmedDrinks;
        }

        return drinks + "\n" + trimmedDrinks;
    }

    private List<String> toDrinkItems() {
        if (drinks == null || drinks.isBlank()) {
            return List.of();
        }

        return Arrays.stream(drinks.split("\\R"))
                .map(String::trim)
                .filter(drink -> !drink.isBlank())
                .toList();
    }
}
