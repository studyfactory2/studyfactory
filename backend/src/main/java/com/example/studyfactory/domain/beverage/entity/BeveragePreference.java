package com.example.studyfactory.domain.beverage.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
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
@Table(name = "beverage_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeveragePreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
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
}
