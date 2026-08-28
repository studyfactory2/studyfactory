package com.example.studyfactory.domain.studyBreak.scheduler;

import com.example.studyfactory.domain.studyBreak.service.StudyBreakReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "study-break.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StudyBreakReconciliationScheduler {

    private final StudyBreakReconciliationService studyBreakReconciliationService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAfterStartup() {
        try {
            reconcile();
        } catch (RuntimeException exception) {
            log.error("휴식시간 공부 기록 시작 복구 실패. 다음 정기 실행에서 재시도합니다.", exception);
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void reconcileActiveSessions() {
        reconcile();
    }

    private void reconcile() {
        int closedSessionCount = studyBreakReconciliationService.reconcileActiveSessions();
        if (closedSessionCount > 0) {
            log.info("휴식시간 공부 기록 자동 종료 완료. 종료 건수: {}", closedSessionCount);
        }
    }
}
