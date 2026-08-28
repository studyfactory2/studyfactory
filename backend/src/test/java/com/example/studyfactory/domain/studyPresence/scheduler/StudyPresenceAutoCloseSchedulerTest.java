package com.example.studyfactory.domain.studyPresence.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoCloseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습실 미퇴실 기록 자동 종료 스케줄러 테스트")
class StudyPresenceAutoCloseSchedulerTest {

    @InjectMocks
    private StudyPresenceAutoCloseScheduler scheduler;

    @Mock
    private StudyPresenceAutoCloseService studyPresenceAutoCloseService;

    @Test
    @DisplayName("애플리케이션 시작 복구 실패는 시작을 중단하지 않고 다음 실행에 맡긴다")
    void tolerateStartupRecoveryFailure() {
        given(studyPresenceAutoCloseService.closeStaleSessions())
                .willThrow(new IllegalStateException("temporary database failure"));

        assertThatCode(scheduler::closeStaleSessionsAfterStartup).doesNotThrowAnyException();
        then(studyPresenceAutoCloseService).should().closeStaleSessions();
    }

    @Test
    @DisplayName("정기 실행은 실패를 숨기지 않아 스케줄러가 기록하고 다음 분에 재시도하게 한다")
    void propagateScheduledFailure() {
        given(studyPresenceAutoCloseService.closeStaleSessions())
                .willThrow(new IllegalStateException("temporary database failure"));

        assertThatThrownBy(scheduler::closeStaleSessions)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary database failure");
    }
}
