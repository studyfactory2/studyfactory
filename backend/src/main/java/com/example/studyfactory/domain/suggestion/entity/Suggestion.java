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
    private boolean isResolved;

    public Suggestion(
            SuggestionReferenceInformation referenceInformation,
            SuggestionCategory category,
            String content,
            boolean isResolved
    ) {
        this.referenceInformation = referenceInformation;
        this.category = category;
        this.content = content;
        this.isResolved = isResolved;
    }

    public Long getMemberId() {
        return referenceInformation.getMemberId();
    }

    public Long getBranchId() {
        return referenceInformation.getBranchId();
    }

    public Long getResolvedByMemberId() {
        return referenceInformation.getResolvedByMemberId();
    }

    public boolean isResolved() {
        return isResolved;
    }

    public void resolve(Long memberId) {
        referenceInformation.resolveBy(memberId);
        isResolved = true;
    }

    public void unresolve() {
        referenceInformation.clearResolver();
        isResolved = false;
    }

    public void toggleResolve(Long memberId) {
        if (isResolved) {
            unresolve();
            return;
        }

        resolve(memberId);
    }
}
