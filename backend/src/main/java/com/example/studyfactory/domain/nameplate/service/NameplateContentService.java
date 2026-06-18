package com.example.studyfactory.domain.nameplate.service;

import com.example.studyfactory.domain.nameplate.dto.NameplateContentCreateRequest;
import com.example.studyfactory.domain.nameplate.dto.NameplateContentResponse;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.exception.NameplateContentException;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NameplateContentService {

    private final NameplateContentRepository nameplateContentRepository;

    @Transactional
    public NameplateContentResponse create(NameplateContentCreateRequest request) {
        String content = request.content().trim();
        if (nameplateContentRepository.existsByContent(content)) {
            throw NameplateContentException.duplicatedContent();
        }

        NameplateContent nameplateContent = request.toEntity();

        return NameplateContentResponse.from(nameplateContentRepository.save(nameplateContent));
    }
}
