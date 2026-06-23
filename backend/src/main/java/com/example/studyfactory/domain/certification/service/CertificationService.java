package com.example.studyfactory.domain.certification.service;

import com.example.studyfactory.domain.certification.dto.CertificationCreateRequest;
import com.example.studyfactory.domain.certification.dto.CertificationResponse;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.exception.CertificationException;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CertificationService {

    private final CertificationRepository certificationRepository;

    @Transactional(readOnly = true)
    public List<CertificationResponse> findAll() {
        return certificationRepository.findAllByOrderByIdAsc()
                .stream()
                .map(CertificationResponse::from)
                .toList();
    }

    @Transactional
    public CertificationResponse create(CertificationCreateRequest request) {
        String content = request.content().trim();
        if (certificationRepository.existsByContent(content)) {
            throw CertificationException.duplicatedContent();
        }

        Certification certification = request.toEntity();

        return CertificationResponse.from(certificationRepository.save(certification));
    }
}
