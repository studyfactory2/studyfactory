package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.studyBreak.service.StudyBreakLifecycleService;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCloseReason;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습실 미퇴실 기록 자동 종료 서비스 테스트")
class StudyPresenceAutoCloseServiceTest {

    private static final Instant RUN_AT = Instant.parse("2026-08-29T01:20:00Z");
    private static final Instant CURRENT_SEOUL_DAY_STARTED_AT = Instant.parse("2026-08-28T15:00:00Z");

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private StudyBreakLifecycleService studyBreakLifecycleService;

    @Test
    @DisplayName("지연 실행되어도 각 기록을 입실 다음 서울 자정으로 종료한다")
    void closeEachSessionAtItsFirstFollowingMidnight() {
        StudyPresenceSession previousDay = new StudyPresenceSession(
                1L,
                2L,
                Instant.parse("2026-08-28T14:59:00Z")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(previousDay, "id", 10L);
        StudyPresenceSession severalDaysOld = new StudyPresenceSession(
                2L,
                3L,
                Instant.parse("2026-08-25T03:00:00Z")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(severalDaysOld, "id", 20L);
        given(studyPresenceSessionRepository.findStaleActiveSessionsForUpdate(CURRENT_SEOUL_DAY_STARTED_AT))
                .willReturn(List.of(severalDaysOld, previousDay));
        StudyPresenceAutoCloseService service = serviceAt(RUN_AT);

        int closedSessionCount = service.closeStaleSessions();

        assertThat(closedSessionCount).isEqualTo(2);
        assertAutomaticallyClosed(previousDay, Instant.parse("2026-08-28T15:00:00Z"));
        assertAutomaticallyClosed(severalDaysOld, Instant.parse("2026-08-25T15:00:00Z"));
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(1L, 10L, Instant.parse("2026-08-28T15:00:00Z"));
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(2L, 20L, Instant.parse("2026-08-25T15:00:00Z"));
    }

    @Test
    @DisplayName("현재 서울 날짜에 입실한 기록은 저장소 경계 조건으로 자동 종료하지 않는다")
    void useCurrentSeoulDayStartAsStrictRepositoryCutoff() {
        given(studyPresenceSessionRepository.findStaleActiveSessionsForUpdate(CURRENT_SEOUL_DAY_STARTED_AT))
                .willReturn(List.of());
        StudyPresenceAutoCloseService service = serviceAt(RUN_AT);

        int closedSessionCount = service.closeStaleSessions();

        assertThat(closedSessionCount).isZero();
        then(studyPresenceSessionRepository).should()
                .findStaleActiveSessionsForUpdate(CURRENT_SEOUL_DAY_STARTED_AT);
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("재시도 목록에 이미 닫힌 기록이 있어도 덮어쓰지 않아 멱등성을 지킨다")
    void skipAlreadyClosedSessionDuringRetry() {
        StudyPresenceSession session = new StudyPresenceSession(
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        session.checkOut(Instant.parse("2026-08-28T14:30:00Z"));
        given(studyPresenceSessionRepository.findStaleActiveSessionsForUpdate(CURRENT_SEOUL_DAY_STARTED_AT))
                .willReturn(List.of(session));
        StudyPresenceAutoCloseService service = serviceAt(RUN_AT);

        int closedSessionCount = service.closeStaleSessions();

        assertThat(closedSessionCount).isZero();
        assertThat(session.getCheckedOutAt()).isEqualTo(Instant.parse("2026-08-28T14:30:00Z"));
        assertThat(session.isAutomaticallyClosed()).isFalse();
        then(studyBreakLifecycleService).shouldHaveNoInteractions();
    }

    private StudyPresenceAutoCloseService serviceAt(Instant instant) {
        return new StudyPresenceAutoCloseService(
                studyPresenceSessionRepository,
                new StudyPresenceAutoClosePolicy(true),
                studyBreakLifecycleService,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private void assertAutomaticallyClosed(StudyPresenceSession session, Instant expectedCheckedOutAt) {
        assertThat(session.getCheckedOutAt()).isEqualTo(expectedCheckedOutAt);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getClosedByMemberId()).isNull();
        assertThat(session.isAutomaticallyClosed()).isTrue();
        assertThat(session.isActive()).isFalse();
    }
}
