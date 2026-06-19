package com.example.studyfactory.domain.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.dto.SuggestionCreateRequest;
import com.example.studyfactory.domain.suggestion.dto.SuggestionResponse;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("건의사항 서비스 테스트")
class SuggestionServiceTest {

    @InjectMocks
    private SuggestionService suggestionService;

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("토큰의 사원과 건의사항 정보로 건의사항을 생성한다")
    void createSuggestion() {
        SuggestionCreateRequest request = new SuggestionCreateRequest(SuggestionCategory.GENERAL, "책상 조명이 어두워요.");
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(suggestionRepository.save(any(Suggestion.class))).willAnswer(invocation -> invocation.getArgument(0));

        SuggestionResponse response = suggestionService.create(1L, request);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.resolvedByMemberId()).isNull();
        assertThat(response.category()).isEqualTo(SuggestionCategory.GENERAL);
        assertThat(response.content()).isEqualTo("책상 조명이 어두워요.");
        assertThat(response.isResolved()).isFalse();
    }

    @Test
    @DisplayName("토큰의 사원 ID로 본인 건의사항 목록을 조회한다")
    void findMine() {
        Suggestion suggestion = new Suggestion(
                new SuggestionReferenceInformation(1L, 2L, null),
                SuggestionCategory.STUDY,
                "스터디룸이 추워요.",
                false
        );
        given(suggestionRepository.findMine(1L)).willReturn(List.of(suggestion));

        List<SuggestionResponse> responses = suggestionService.findMine(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).memberId()).isEqualTo(1L);
        assertThat(responses.get(0).branchId()).isEqualTo(2L);
        assertThat(responses.get(0).resolvedByMemberId()).isNull();
        assertThat(responses.get(0).category()).isEqualTo(SuggestionCategory.STUDY);
        assertThat(responses.get(0).content()).isEqualTo("스터디룸이 추워요.");
        assertThat(responses.get(0).isResolved()).isFalse();
        then(suggestionRepository).should().findMine(1L);
    }

    private Member createMember() {
        return new Member(
                2L,
                3L,
                "kim",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                4L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
