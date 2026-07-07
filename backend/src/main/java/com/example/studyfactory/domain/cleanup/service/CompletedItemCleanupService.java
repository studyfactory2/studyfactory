package com.example.studyfactory.domain.cleanup.service;

import com.example.studyfactory.domain.suggestion.repository.SuggestionRepository;
import com.example.studyfactory.domain.todo.repository.TodoItemRepository;
import com.example.studyfactory.domain.todo.repository.TodoReplyRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompletedItemCleanupService {

    private static final int RETENTION_DAYS = 3;

    private final TodoItemRepository todoItemRepository;
    private final TodoReplyRepository todoReplyRepository;
    private final SuggestionRepository suggestionRepository;

    @Transactional
    public CleanupResult deleteCompletedItems() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deletedTodoCount = deleteCompletedTodos(threshold);
        int deletedSuggestionCount = suggestionRepository.deleteResolvedBefore(threshold);

        return new CleanupResult(deletedTodoCount, deletedSuggestionCount);
    }

    private int deleteCompletedTodos(LocalDateTime threshold) {
        List<Long> todoItemIds = todoItemRepository.findCompletedIdsBefore(threshold);
        if (todoItemIds.isEmpty()) {
            return 0;
        }

        todoReplyRepository.deleteByTodoItemIdIn(todoItemIds);
        todoItemRepository.deleteByIdIn(todoItemIds);

        return todoItemIds.size();
    }

    public record CleanupResult(
            int deletedTodoCount,
            int deletedSuggestionCount
    ) {
    }
}
