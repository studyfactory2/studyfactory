package com.example.studyfactory.domain.studyPresence.scheduler;

import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoCloseService;
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
        name = "study-presence.auto-close.enabled",
        havingValue = "true"
)
public class StudyPresenceAutoCloseScheduler {

    private final StudyPresenceAutoCloseService studyPresenceAutoCloseService;

    @EventListener(ApplicationReadyEvent.class)
    public void closeStaleSessionsAfterStartup() {
        try {
            reconcileStaleSessions();
        } catch (RuntimeException exception) {
            log.error("미퇴실 기록 시작 복구 실패. 다음 정기 실행에서 재시도합니다.", exception);
        }
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void closeStaleSessions() {
        reconcileStaleSessions();
    }

    private void reconcileStaleSessions() {
        int closedSessionCount = studyPresenceAutoCloseService.closeStaleSessions();
        if (closedSessionCount > 0) {
            log.info("자정 기준 미퇴실 기록 자동 종료 완료. 종료 건수: {}", closedSessionCount);
        }
    }
}
