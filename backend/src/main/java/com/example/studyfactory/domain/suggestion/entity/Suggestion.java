package com.example.studyfactory.domain.suggestion.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private SuggestionReferenceInformation referenceInformation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SuggestionCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean resolved;

    public Suggestion(
            SuggestionReferenceInformation referenceInformation,
            SuggestionCategory category,
            String content,
            boolean resolved
    ) {
        this.referenceInformation = referenceInformation;
        this.category = category;
        this.content = content;
        this.resolved = resolved;
    }
}
