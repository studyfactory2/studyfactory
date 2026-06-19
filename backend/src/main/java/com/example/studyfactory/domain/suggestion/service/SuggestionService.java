package com.example.studyfactory.domain.suggestion.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.dto.SuggestionCreateRequest;
import com.example.studyfactory.domain.suggestion.dto.SuggestionResponse;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public SuggestionResponse create(Long memberId, SuggestionCreateRequest request) {
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        Suggestion suggestion = new Suggestion(
                new SuggestionReferenceInformation(member.getId(), member.getBranchId(), null),
                request.category(),
                request.content(),
                false
        );

        return SuggestionResponse.from(suggestionRepository.save(suggestion));
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> findMine(Long memberId) {
        return suggestionRepository.findMine(memberId)
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }
}
