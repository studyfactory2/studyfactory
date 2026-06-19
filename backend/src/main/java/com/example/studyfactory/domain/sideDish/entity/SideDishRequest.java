package com.example.studyfactory.domain.sideDish.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "side_dish_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SideDishRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private SideDishReferenceInformation referenceInformation;

    @Embedded
    private SideDishMealInformation mealInformation;

    @Embedded
    private SideDishOrderInformation orderInformation;

    public SideDishRequest(
            SideDishReferenceInformation referenceInformation,
            SideDishMealInformation mealInformation,
            SideDishOrderInformation orderInformation
    ) {
        this.referenceInformation = referenceInformation;
        this.mealInformation = mealInformation;
        this.orderInformation = orderInformation;
    }
}
