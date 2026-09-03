package com.example.studyfactory.domain.todo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.suggestion.entity.SuggestionCategory;
import com.example.studyfactory.domain.suggestion.entity.SuggestionReferenceInformation;
import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import com.example.studyfactory.domain.todo.repository.TodoItemRepository;
import com.example.studyfactory.domain.todo.repository.TodoReplyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("할 일 서비스 테스트")
class TodoServiceTest {

    /** 2026-10-01 00:30 Asia/Seoul — still 2026-09-30 in UTC. */
    private static final Instant JUST_AFTER_SEOUL_MIDNIGHT = Instant.parse("2026-09-30T15:30:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.of(2026, 10, 1);

    @InjectMocks
    private TodoService todoService;

    @Mock
    private TodoItemRepository todoItemRepository;

    @Mock
    private TodoReplyRepository todoReplyRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BeverageItemRepository beverageItemRepository;

    @Mock
    private CertificationRepository certificationRepository;

    @Spy
    private Clock clock = Clock.fixed(JUST_AFTER_SEOUL_MIDNIGHT, ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("건의로 만든 할 일은 서울 기준 오늘 날짜로 저장된다")
    void createSuggestionTodoOnSeoulDate() {
        Suggestion suggestion = createSuggestion();
        given(todoItemRepository.existsBySourceTypeAndSourceIdAndTodoDate(
                TodoSourceType.SUGGESTION,
                7L,
                SEOUL_TODAY
        )).willReturn(false);
        given(memberRepository.findById(5L)).willReturn(Optional.of(createMember()));

        todoService.createSuggestionTodo(suggestion);

        ArgumentCaptor<TodoItem> savedTodo = ArgumentCaptor.forClass(TodoItem.class);
        then(todoItemRepository).should().save(savedTodo.capture());
        assertThat(savedTodo.getValue().getTodoDate()).isEqualTo(SEOUL_TODAY);
    }

    @Test
    @DisplayName("서울 기준 오늘 이미 만들어진 건의 할 일은 다시 만들지 않는다")
    void skipDuplicateSuggestionTodoForSeoulToday() {
        given(todoItemRepository.existsBySourceTypeAndSourceIdAndTodoDate(
                TodoSourceType.SUGGESTION,
                7L,
                SEOUL_TODAY
        )).willReturn(true);

        todoService.createSuggestionTodo(createSuggestion());

        then(todoItemRepository).should(never()).save(org.mockito.ArgumentMatchers.any(TodoItem.class));
    }

    private Suggestion createSuggestion() {
        Suggestion suggestion = new Suggestion(
                new SuggestionReferenceInformation(5L, 2L, null),
                SuggestionCategory.SUPPLIES,
                "물티슈가 떨어졌어요",
                false
        );
        ReflectionTestUtils.setField(suggestion, "id", 7L);
        return suggestion;
    }

    private Member createMember() {
        Member member = new Member(
                2L,
                "김회원",
                "password123",
                MemberRole.MEMBER,
                10,
                LocalDate.of(2026, 8, 1),
                null
        );
        ReflectionTestUtils.setField(member, "id", 5L);
        return member;
    }
}
