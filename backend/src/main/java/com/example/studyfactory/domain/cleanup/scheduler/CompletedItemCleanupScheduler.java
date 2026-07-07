package com.example.studyfactory.domain.cleanup.scheduler;

import com.example.studyfactory.domain.cleanup.service.CompletedItemCleanupService;
import com.example.studyfactory.domain.cleanup.service.CompletedItemCleanupService.CleanupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompletedItemCleanupScheduler {

    private final CompletedItemCleanupService completedItemCleanupService;

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Seoul")
    public void deleteCompletedItems() {
        CleanupResult result = completedItemCleanupService.deleteCompletedItems();
        log.info(
                "완료 항목 자동 삭제 완료. 할일: {}, 회원건의: {}",
                result.deletedTodoCount(),
                result.deletedSuggestionCount()
        );
    }
}
