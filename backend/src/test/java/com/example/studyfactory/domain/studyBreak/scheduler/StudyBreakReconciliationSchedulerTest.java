package com.example.studyfactory.domain.studyBreak.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.studyBreak.service.StudyBreakReconciliationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴식시간 공부 세션 복구 스케줄러 테스트")
class StudyBreakReconciliationSchedulerTest {

    @Mock
    private StudyBreakReconciliationService studyBreakReconciliationService;

    @Test
    @DisplayName("서버 시작 복구가 실패해도 애플리케이션 시작을 막지 않는다")
    void swallowStartupFailureForScheduledRetry() {
        RuntimeException failure = new RuntimeException("database temporarily unavailable");
        given(studyBreakReconciliationService.reconcileActiveSessions()).willThrow(failure);

        assertThatCode(() -> scheduler().reconcileAfterStartup()).doesNotThrowAnyException();

        then(studyBreakReconciliationService).should().reconcileActiveSessions();
    }

    @Test
    @DisplayName("정기 복구 실패는 스프링 스케줄러에 전파한다")
    void propagateScheduledFailure() {
        RuntimeException failure = new RuntimeException("database temporarily unavailable");
        given(studyBreakReconciliationService.reconcileActiveSessions()).willThrow(failure);

        assertThatThrownBy(() -> scheduler().reconcileActiveSessions()).isSameAs(failure);

        then(studyBreakReconciliationService).should().reconcileActiveSessions();
    }

    @Test
    @DisplayName("정기 실행은 복구 서비스를 한 번 호출한다")
    void delegateScheduledReconciliation() {
        given(studyBreakReconciliationService.reconcileActiveSessions()).willReturn(2);

        scheduler().reconcileActiveSessions();

        then(studyBreakReconciliationService).should().reconcileActiveSessions();
        then(studyBreakReconciliationService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("서버 시작 이벤트와 서울 기준 매분 실행 계약을 유지한다")
    void keepSchedulingAnnotations() throws NoSuchMethodException {
        EventListener startup = StudyBreakReconciliationScheduler.class
                .getMethod("reconcileAfterStartup")
                .getAnnotation(EventListener.class);
        Scheduled scheduled = StudyBreakReconciliationScheduler.class
                .getMethod("reconcileActiveSessions")
                .getAnnotation(Scheduled.class);

        assertThat(startup).isNotNull();
        assertThat(startup.value()).containsExactly(ApplicationReadyEvent.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private StudyBreakReconciliationScheduler scheduler() {
        return new StudyBreakReconciliationScheduler(studyBreakReconciliationService);
    }
}
