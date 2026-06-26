package com.example.studyfactory.domain.suggestion.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.dto.SuggestionCreateRequest;
import com.example.studyfactory.domain.suggestion.dto.SuggestionResponse;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import com.example.studyfactory.domain.todo.service.TodoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final MemberRepository memberRepository;
    private final TodoService todoService;

    @Transactional
    public SuggestionResponse create(Long memberId, SuggestionCreateRequest request) {
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        Suggestion suggestion = new Suggestion(
                new SuggestionReferenceInformation(member.getId(), member.getBranchId(), null),
                request.category(),
                request.content(),
                false
        );

        Suggestion savedSuggestion = suggestionRepository.save(suggestion);
        todoService.createSuggestionTodo(savedSuggestion);

        return SuggestionResponse.from(savedSuggestion);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> findMine(Long memberId) {
        return suggestionRepository.findMine(memberId)
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> findAll() {
        return suggestionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }
}
