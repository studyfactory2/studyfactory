package com.example.studyfactory.domain.todo.service;

import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.suggestion.entity.Suggestion;
import com.example.studyfactory.domain.todo.dto.TodoCompletionRequest;
import com.example.studyfactory.domain.todo.dto.TodoCreateRequest;
import com.example.studyfactory.domain.todo.dto.TodoReplyRequest;
import com.example.studyfactory.domain.todo.dto.TodoReplyResponse;
import com.example.studyfactory.domain.todo.dto.TodoResponse;
import com.example.studyfactory.domain.todo.dto.TodoUpdateRequest;
import com.example.studyfactory.domain.todo.entity.TodoItem;
import com.example.studyfactory.domain.todo.entity.TodoPriority;
import com.example.studyfactory.domain.todo.entity.TodoReply;
import com.example.studyfactory.domain.todo.entity.TodoSourceType;
import com.example.studyfactory.domain.todo.repository.TodoItemRepository;
import com.example.studyfactory.domain.todo.repository.TodoReplyRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoItemRepository todoItemRepository;
    private final TodoReplyRepository todoReplyRepository;
    private final MemberRepository memberRepository;
    private final CertificationRepository certificationRepository;

    @Transactional(readOnly = true)
    public List<TodoResponse> findDaily(Long branchId, LocalDate date) {
        return todoItemRepository.findByBranchIdAndTodoDateOrderByCompletedAscPriorityDescCreatedAtAsc(branchId, date)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<TodoResponse> findDailyWithJoinTodos(Long branchId, LocalDate date) {
        createJoinTodos(branchId, date);
        return findDaily(branchId, date);
    }

    @Transactional
    public TodoResponse create(Long currentMemberId, TodoCreateRequest request) {
        Member currentMember = findMember(currentMemberId);
        TodoItem todoItem = new TodoItem(
                request.branchId(),
                request.todoDate(),
                request.content().trim(),
                request.priority(),
                TodoSourceType.MANUAL,
                null,
                null,
                currentMember.getId(),
                currentMember.getName()
        );

        return toResponse(todoItemRepository.save(todoItem));
    }

    @Transactional
    public TodoResponse updateCompletion(Long currentMemberId, Long todoId, TodoCompletionRequest request) {
        Member currentMember = findMember(currentMemberId);
        TodoItem todoItem = todoItemRepository.findById(todoId).orElseThrow(MemberException::forbidden);
        todoItem.updateCompletion(request.completed(), currentMember.getId());

        return toResponse(todoItem);
    }

    @Transactional
    public TodoResponse update(Long currentMemberId, Long todoId, TodoUpdateRequest request) {
        findMember(currentMemberId);
        TodoItem todoItem = todoItemRepository.findById(todoId).orElseThrow(MemberException::forbidden);
        todoItem.updateContent(request.content().trim());

        return toResponse(todoItem);
    }

    @Transactional
    public TodoResponse updateReply(Long currentMemberId, Long todoId, TodoReplyRequest request) {
        Member currentMember = findMember(currentMemberId);
        TodoItem todoItem = todoItemRepository.findById(todoId).orElseThrow(MemberException::forbidden);
        todoReplyRepository.save(new TodoReply(todoItem.getId(), currentMember.getId(), currentMember.getName(), request.replyContent().trim()));

        return toResponse(todoItem);
    }

    @Transactional
    public void delete(Long currentMemberId, Long todoId) {
        findMember(currentMemberId);
        TodoItem todoItem = todoItemRepository.findById(todoId).orElseThrow(MemberException::forbidden);
        if (todoItem.getSourceType() == TodoSourceType.JOIN_MEMBER) {
            throw MemberException.forbidden();
        }

        todoReplyRepository.deleteByTodoItemId(todoItem.getId());
        todoItemRepository.delete(todoItem);
    }

    @Transactional
    public void createSuggestionTodo(Suggestion suggestion) {
        if (todoItemRepository.existsBySourceTypeAndSourceIdAndTodoDate(
                TodoSourceType.SUGGESTION,
                suggestion.getId(),
                LocalDate.now()
        )) {
            return;
        }

        todoItemRepository.save(new TodoItem(
                suggestion.getBranchId(),
                LocalDate.now(),
                suggestion.getContent(),
                TodoPriority.NORMAL,
                TodoSourceType.SUGGESTION,
                suggestion.getId(),
                suggestion.getMemberId(),
                suggestion.getMemberId(),
                findMember(suggestion.getMemberId()).getName()
        ));
    }

    private void createJoinTodos(Long branchId, LocalDate date) {
        memberRepository.findByWorkInformationJoinDateAndReferenceInformationBranchIdOrderByIdAsc(date, branchId)
                .forEach(member -> createJoinTodo(date, member));
    }

    private void createJoinTodo(LocalDate date, Member member) {
        if (todoItemRepository.existsBySourceTypeAndSourceIdAndTodoDate(TodoSourceType.JOIN_MEMBER, member.getId(), date)) {
            return;
        }

        todoItemRepository.save(new TodoItem(
                member.getBranchId(),
                date,
                toJoinTodoContent(member),
                TodoPriority.NORMAL,
                TodoSourceType.JOIN_MEMBER,
                member.getId(),
                member.getId(),
                null,
                null
        ));
    }

    private String toJoinTodoContent(Member member) {
        String certification = member.getCertificationId() == null
                ? ""
                : certificationRepository.findById(member.getCertificationId())
                .map(certificationEntity -> " " + certificationEntity.getContent())
                .orElse("");
        String seat = member.getSeatNumber() == null ? "" : member.getSeatNumber() + "번 ";

        return "자격증" + seat + member.getName() + certification + " 신규";
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private TodoResponse toResponse(TodoItem todoItem) {
        List<TodoReplyResponse> replies = todoReplyRepository.findByTodoItemIdOrderByCreatedAtAsc(todoItem.getId())
                .stream()
                .map(TodoReplyResponse::from)
                .toList();

        return TodoResponse.from(todoItem, replies);
    }
}
