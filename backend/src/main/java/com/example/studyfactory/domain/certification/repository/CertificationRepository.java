package com.example.studyfactory.domain.certification.repository;

import com.example.studyfactory.domain.certification.entity.Certification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

    boolean existsByContent(String content);

    Optional<Certification> findByContent(String content);

    List<Certification> findAllByOrderByIdAsc();
}
