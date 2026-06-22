package com.example.studyfactory.domain.nameplate.repository;

import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NameplateContentRepository extends JpaRepository<NameplateContent, Long> {

    boolean existsByContent(String content);

    Optional<NameplateContent> findByContent(String content);

    List<NameplateContent> findAllByOrderByIdAsc();
}
