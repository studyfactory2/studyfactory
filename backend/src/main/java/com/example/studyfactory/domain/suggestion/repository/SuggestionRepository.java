package com.example.studyfactory.domain.suggestion.repository;

import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
}
