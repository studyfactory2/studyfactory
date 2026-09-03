package com.example.studyfactory.domain.suggestion.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.dto.SuggestionCreateRequest;
import com.example.studyfactory.domain.suggestion.dto.SuggestionResponse;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Suggestion savedSuggestion = suggestionRepository.save(suggestion);

        return SuggestionResponse.from(savedSuggestion);
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> findMine(Long memberId) {
        return suggestionRepository.findMine(memberId)
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    /** Operations view: managers see their own branch's suggestions, nobody else's. */
    @Transactional(readOnly = true)
    public List<SuggestionResponse> findAll(Long currentMemberId) {
        Member currentMember = findOperationsMember(currentMemberId);
        List<Suggestion> suggestions = suggestionRepository.findByBranchId(currentMember.getBranchId());
        Map<Long, String> memberNames = findMemberNames(suggestions);

        return suggestions.stream()
                .map(suggestion -> SuggestionResponse.from(
                        suggestion,
                        memberNames.get(suggestion.getMemberId()),
                        memberNames.get(suggestion.getResolvedByMemberId())
                ))
                .toList();
    }

    @Transactional
    public SuggestionResponse resolve(Long currentMemberId, Long suggestionId) {
        Member currentMember = findOperationsMember(currentMemberId);
        Suggestion suggestion = suggestionRepository.findById(suggestionId).orElseThrow(MemberException::forbidden);
        validateSameBranch(currentMember, suggestion);
        suggestion.toggleResolve(currentMember.getId());

        String memberName = memberRepository.findById(suggestion.getMemberId())
                .map(Member::getName)
                .orElse(null);
        String resolvedByMemberName = suggestion.isResolved() ? currentMember.getName() : null;

        return SuggestionResponse.from(suggestion, memberName, resolvedByMemberName);
    }

    /** Reading or resolving other people's suggestions is a manager action. */
    private Member findOperationsMember(Long currentMemberId) {
        Member currentMember = memberRepository.findById(currentMemberId)
                .orElseThrow(MemberException::memberNotFound);
        if (!currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }

        return currentMember;
    }

    private void validateSameBranch(Member currentMember, Suggestion suggestion) {
        if (!currentMember.getBranchId().equals(suggestion.getBranchId())) {
            throw MemberException.forbidden();
        }
    }

    private Map<Long, String> findMemberNames(List<Suggestion> suggestions) {
        List<Long> memberIds = suggestions.stream()
                .flatMap(suggestion -> java.util.stream.Stream.of(suggestion.getMemberId(), suggestion.getResolvedByMemberId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> memberNames = new HashMap<>();
        memberRepository.findAllById(memberIds).forEach(member -> memberNames.put(member.getId(), member.getName()));

        return memberNames;
    }
}
