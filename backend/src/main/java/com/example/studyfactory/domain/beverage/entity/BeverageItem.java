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
@Table(name = "beverage_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BeverageItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String note;

    public BeverageItem(Long memberId, String name, String note) {
        this.memberId = memberId;
        this.name = name;
        this.note = normalizeNote(note);
    }

    private String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
