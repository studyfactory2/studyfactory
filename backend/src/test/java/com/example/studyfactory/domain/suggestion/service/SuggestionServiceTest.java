package com.example.studyfactory.domain.suggestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
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
        then(suggestionRepository).should().save(any(Suggestion.class));
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

    @Test
    @DisplayName("운영 권한자는 자기 지점의 건의사항만 조회한다")
    void findAllWithinOperatorBranch() {
        Suggestion branchSuggestion = new Suggestion(
                new SuggestionReferenceInformation(1L, 2L, null),
                SuggestionCategory.STUDY,
                "스터디룸이 추워요.",
                false
        );
        given(memberRepository.findById(9L)).willReturn(Optional.of(createOperator(9L, 2L, MemberRole.ADMIN)));
        given(suggestionRepository.findByBranchId(2L)).willReturn(List.of(branchSuggestion));

        List<SuggestionResponse> responses = suggestionService.findAll(9L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().memberId()).isEqualTo(1L);
        assertThat(responses.getFirst().category()).isEqualTo(SuggestionCategory.STUDY);
        then(suggestionRepository).should().findByBranchId(2L);
    }

    @Test
    @DisplayName("일반 회원은 전체 건의사항 목록을 조회할 수 없다")
    void rejectFindAllForMember() {
        given(memberRepository.findById(7L)).willReturn(Optional.of(createOperator(7L, 2L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> suggestionService.findAll(7L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("일반 회원은 건의사항을 해결 처리할 수 없다")
    void rejectResolveForMember() {
        given(memberRepository.findById(7L)).willReturn(Optional.of(createOperator(7L, 2L, MemberRole.MEMBER)));

        assertThatThrownBy(() -> suggestionService.resolve(7L, 1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    @DisplayName("다른 지점의 건의사항은 해결 처리할 수 없다")
    void rejectResolveForOtherBranch() {
        Suggestion otherBranchSuggestion = new Suggestion(
                new SuggestionReferenceInformation(1L, 5L, null),
                SuggestionCategory.GENERAL,
                "화장실 비품이 부족해요.",
                false
        );
        given(memberRepository.findById(9L)).willReturn(Optional.of(createOperator(9L, 2L, MemberRole.ADMIN)));
        given(suggestionRepository.findById(1L)).willReturn(Optional.of(otherBranchSuggestion));

        assertThatThrownBy(() -> suggestionService.resolve(9L, 1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        assertThat(otherBranchSuggestion.isResolved()).isFalse();
    }

    private Member createOperator(Long id, Long branchId, MemberRole role) {
        Member operator = new Member(
                branchId,
                "운영자",
                "password123",
                role,
                1,
                LocalDate.of(2026, 7, 1),
                null
        );
        ReflectionTestUtils.setField(operator, "id", id);
        return operator;
    }

    private Member createMember() {
        return new Member(
                2L,
                "kim",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                4L,
                "오전 교육 예정"
        );
    }
}
