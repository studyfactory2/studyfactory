package com.example.studyfactory.domain.todo.service;

import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.certification.entity.Certification;
import com.example.studyfactory.domain.certification.repository.CertificationRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoItemRepository todoItemRepository;
    private final TodoReplyRepository todoReplyRepository;
    private final MemberRepository memberRepository;
    private final BeverageItemRepository beverageItemRepository;
    private final CertificationRepository certificationRepository;
    private final Clock clock;

    /**
     * The daily board is branch-wide operations work — it is fed by member
     * suggestions and by new-member joins — so every entry point below is
     * ADMIN/STAFF only. The join todos are still materialized lazily on read;
     * splitting that write out belongs with the staff 할 일 screen, which will
     * have somewhere to call it from.
     */
    @Transactional
    public List<TodoResponse> findDailyWithJoinTodos(Long currentMemberId, Long branchId, LocalDate date) {
        Member operator = findOperationsMember(currentMemberId);
        Long targetBranchId = resolveBranchId(operator, branchId);
        createJoinTodos(targetBranchId, date);

        return findDaily(targetBranchId, date);
    }

    @Transactional
    public TodoResponse create(Long currentMemberId, TodoCreateRequest request) {
        Member operator = findOperationsMember(currentMemberId);
        validateBranchScope(operator, request.branchId());
        TodoItem todoItem = new TodoItem(
                request.branchId(),
                request.todoDate(),
                request.content().trim(),
                request.priority(),
                TodoSourceType.MANUAL,
                null,
                null,
                operator.getId(),
                operator.getName()
        );

        return toResponse(todoItemRepository.save(todoItem));
    }

    @Transactional
    public TodoResponse updateCompletion(Long currentMemberId, Long todoId, TodoCompletionRequest request) {
        Member operator = findOperationsMember(currentMemberId);
        TodoItem todoItem = findTodoInScope(operator, todoId);
        todoItem.updateCompletion(request.completed(), operator.getId());

        return toResponse(todoItem);
    }

    @Transactional
    public TodoResponse update(Long currentMemberId, Long todoId, TodoUpdateRequest request) {
        Member operator = findOperationsMember(currentMemberId);
        TodoItem todoItem = findTodoInScope(operator, todoId);
        todoItem.updateContent(request.content().trim());

        return toResponse(todoItem);
    }

    @Transactional
    public TodoResponse updateReply(Long currentMemberId, Long todoId, TodoReplyRequest request) {
        Member operator = findOperationsMember(currentMemberId);
        TodoItem todoItem = findTodoInScope(operator, todoId);
        todoReplyRepository.save(new TodoReply(todoItem.getId(), operator.getId(), operator.getName(), request.replyContent().trim()));

        return toResponse(todoItem);
    }

    @Transactional
    public void delete(Long currentMemberId, Long todoId) {
        Member operator = findOperationsMember(currentMemberId);
        TodoItem todoItem = findTodoInScope(operator, todoId);
        if (todoItem.getSourceType() == TodoSourceType.JOIN_MEMBER) {
            throw MemberException.forbidden();
        }

        todoReplyRepository.deleteByTodoItemId(todoItem.getId());
        todoItemRepository.delete(todoItem);
    }

    private List<TodoResponse> findDaily(Long branchId, LocalDate date) {
        return todoItemRepository.findByBranchIdAndTodoDateOrderByCompletedAscPriorityDescCreatedAtAsc(branchId, date)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Member findOperationsMember(Long currentMemberId) {
        Member currentMember = memberRepository.findById(currentMemberId)
                .orElseThrow(MemberException::memberNotFound);
        if (!currentMember.hasAllPermissions()) {
            throw MemberException.forbidden();
        }

        return currentMember;
    }

    /** A missing todo is refused rather than reported, so ids stay unprobeable. */
    private TodoItem findTodoInScope(Member operator, Long todoId) {
        TodoItem todoItem = todoItemRepository.findById(todoId).orElseThrow(MemberException::forbidden);
        validateBranchScope(operator, todoItem.getBranchId());

        return todoItem;
    }

    /** ADMIN may work across branches; STAFF is pinned to their own. */
    private Long resolveBranchId(Member operator, Long branchId) {
        if (branchId == null) {
            return operator.getBranchId();
        }
        validateBranchScope(operator, branchId);

        return branchId;
    }

    private void validateBranchScope(Member operator, Long branchId) {
        if (operator.getRole() != MemberRole.ADMIN && !operator.getBranchId().equals(branchId)) {
            throw MemberException.forbidden();
        }
    }

    @Transactional
    public void createSuggestionTodo(Suggestion suggestion) {
        if (todoItemRepository.existsBySourceTypeAndSourceIdAndTodoDate(
                TodoSourceType.SUGGESTION,
                suggestion.getId(),
                LocalDate.now(clock)
        )) {
            return;
        }

        todoItemRepository.save(new TodoItem(
                suggestion.getBranchId(),
                LocalDate.now(clock),
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
        String content = toJoinTodoContent(member);
        var existingTodo = todoItemRepository.findBySourceTypeAndSourceIdAndTodoDate(TodoSourceType.JOIN_MEMBER, member.getId(), date);
        if (existingTodo.isPresent()) {
            existingTodo.get().updateContent(content);
            return;
        }

        todoItemRepository.save(new TodoItem(
                member.getBranchId(),
                date,
                content,
                TodoPriority.NORMAL,
                TodoSourceType.JOIN_MEMBER,
                member.getId(),
                member.getId(),
                null,
                null
        ));
    }

    private String toJoinTodoContent(Member member) {
        String seat = member.getSeatNumber() == null ? "" : member.getSeatNumber() + "번 ";
        String certification = getCertificationContent(member);
        String certificationText = certification == null || certification.isBlank() ? "" : " (" + certification + ")";
        String drinks = beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(member.getId())
                .stream()
                .map(item -> item.getName())
                .filter(drink -> !isExcludedDrinkName(drink))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");

        String content = seat + member.getName() + certificationText;
        if (drinks.isBlank()) {
            return content;
        }

        return content + " / 음료: " + drinks;
    }

    private String getCertificationContent(Member member) {
        if (member.getCertificationId() == null) {
            return null;
        }

        return certificationRepository.findById(member.getCertificationId())
                .map(Certification::getContent)
                .orElse(null);
    }

    private boolean isExcludedDrinkName(String drink) {
        String normalizedDrink = drink.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);

        return normalizedDrink.equals("없음") || normalizedDrink.equals("안먹음") || normalizedDrink.equals("x");
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
