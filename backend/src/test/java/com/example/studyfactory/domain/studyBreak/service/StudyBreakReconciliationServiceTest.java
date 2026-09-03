package com.example.studyfactory.domain.studyBreak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakReconciliationCandidate;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴식시간 공부 세션 복구 서비스 테스트")
class StudyBreakReconciliationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRESENCE_SESSION_ID = 11L;
    private static final Long BREAK_SESSION_ID = 21L;
    private static final Instant NOW = Instant.parse("2026-08-28T01:50:00Z");
    private static final Instant WINDOW_STARTED_AT = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant WINDOW_ENDED_AT = Instant.parse("2026-08-28T01:45:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-08-28T01:35:00Z");

    @Mock
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private StudyPresenceAutoClosePolicy presenceAutoClosePolicy;

    @Test
    @DisplayName("입실 기록이 건강하면 지난 휴식 세션을 휴식 종료 경계에서 닫는다")
    void closeExpiredSessionAtBreakBoundary() {
        StudyBreakSession breakSession = newBreakSession();
        StudyPresenceSession presenceSession = newActivePresence(
                PRESENCE_SESSION_ID,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        givenCandidate(breakSession, Optional.of(presenceSession));
        given(presenceAutoClosePolicy.shouldAutomaticallyClose(presenceSession, NOW)).willReturn(false);

        int closedSessionCount = serviceAt(NOW).reconcileActiveSessions();

        assertThat(closedSessionCount).isEqualTo(1);
        assertClosed(breakSession, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("이미 퇴실한 입실 기록은 해당 퇴실 시각으로 휴식 세션을 닫는다")
    void closeForAlreadyClosedPresence() {
        StudyBreakSession breakSession = newBreakSession();
        StudyPresenceSession presenceSession = newActivePresence(
                PRESENCE_SESSION_ID,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        Instant checkedOutAt = Instant.parse("2026-08-28T01:40:00Z");
        presenceSession.checkOut(checkedOutAt);
        givenCandidate(breakSession, Optional.of(presenceSession));

        int closedSessionCount = serviceAt(NOW).reconcileActiveSessions();

        assertThat(closedSessionCount).isEqualTo(1);
        assertClosed(breakSession, checkedOutAt, StudyBreakEndReason.PRESENCE_ENDED);
        then(presenceAutoClosePolicy).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입실 기록이 사라진 고아 휴식 세션을 복구 실행 시각에 닫는다")
    void closeOrphanSessionAtReconciliationTime() {
        StudyBreakSession breakSession = newBreakSession();
        Instant reconcileAt = Instant.parse("2026-08-28T01:40:00Z");
        givenCandidate(breakSession, Optional.empty());

        int closedSessionCount = serviceAt(reconcileAt).reconcileActiveSessions();

        assertThat(closedSessionCount).isEqualTo(1);
        assertClosed(breakSession, reconcileAt, StudyBreakEndReason.PRESENCE_ENDED);
        then(presenceAutoClosePolicy).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("서울 자정을 넘긴 입실 기록은 첫 자정 시각으로 휴식 세션을 닫는다")
    void closeStalePresenceAtItsFirstSeoulMidnight() {
        StudyBreakSession breakSession = newBreakSession();
        StudyPresenceSession presenceSession = newActivePresence(
                PRESENCE_SESSION_ID,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        Instant reconcileAt = Instant.parse("2026-08-28T15:00:05Z");
        Instant firstMidnight = Instant.parse("2026-08-28T15:00:00Z");
        givenCandidate(breakSession, Optional.of(presenceSession));
        given(presenceAutoClosePolicy.shouldAutomaticallyClose(presenceSession, reconcileAt))
                .willReturn(true);
        given(presenceAutoClosePolicy.firstMidnightAfter(presenceSession.getCheckedInAt()))
                .willReturn(firstMidnight);

        int closedSessionCount = serviceAt(reconcileAt).reconcileActiveSessions();

        assertThat(closedSessionCount).isEqualTo(1);
        assertClosed(breakSession, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
        then(presenceAutoClosePolicy).should()
                .firstMidnightAfter(presenceSession.getCheckedInAt());
    }

    @Test
    @DisplayName("현재 입실과 휴식 구간이 모두 건강하면 활성 세션을 유지한다")
    void keepHealthySessionActive() {
        StudyBreakSession breakSession = newBreakSession();
        StudyPresenceSession presenceSession = newActivePresence(
                PRESENCE_SESSION_ID,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        Instant reconcileAt = Instant.parse("2026-08-28T01:40:00Z");
        givenCandidate(breakSession, Optional.of(presenceSession));
        given(presenceAutoClosePolicy.shouldAutomaticallyClose(presenceSession, reconcileAt))
                .willReturn(false);

        int closedSessionCount = serviceAt(reconcileAt).reconcileActiveSessions();

        assertThat(closedSessionCount).isZero();
        assertThat(breakSession.isActive()).isTrue();
        assertThat(breakSession.getEndedAt()).isNull();
        assertThat(breakSession.getEndReason()).isNull();
        then(presenceAutoClosePolicy).should(never())
                .firstMidnightAfter(presenceSession.getCheckedInAt());
    }

    @Test
    @DisplayName("후보 조회 뒤 이미 종료된 휴식 세션은 다시 변경하지 않는다")
    void skipCandidateThatClosedBeforeItsBreakLock() {
        StudyPresenceSession presenceSession = newActivePresence(
                PRESENCE_SESSION_ID,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        given(studyBreakSessionRepository.findActiveReconciliationCandidates())
                .willReturn(List.of(new StudyBreakReconciliationCandidate(
                        BREAK_SESSION_ID,
                        PRESENCE_SESSION_ID
                )));
        given(studyPresenceSessionRepository.findByIdForUpdate(PRESENCE_SESSION_ID))
                .willReturn(Optional.of(presenceSession));
        given(studyBreakSessionRepository.findActiveByIdForUpdate(BREAK_SESSION_ID))
                .willReturn(Optional.empty());

        int closedSessionCount = serviceAt(NOW).reconcileActiveSessions();

        assertThat(closedSessionCount).isZero();
        then(presenceAutoClosePolicy).shouldHaveNoInteractions();
    }

    private StudyBreakReconciliationService serviceAt(Instant instant) {
        return new StudyBreakReconciliationService(
                studyBreakSessionRepository,
                studyPresenceSessionRepository,
                presenceAutoClosePolicy,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private StudyBreakSession newBreakSession() {
        StudyBreakSession session = new StudyBreakSession(
                PRESENCE_SESSION_ID,
                MEMBER_ID,
                2L,
                LocalDate.of(2026, 8, 28),
                StudyBreak.AFTER_FIRST,
                WINDOW_STARTED_AT,
                WINDOW_ENDED_AT,
                STARTED_AT
        );
        ReflectionTestUtils.setField(session, "id", BREAK_SESSION_ID);
        return session;
    }

    private void givenCandidate(
            StudyBreakSession breakSession,
            Optional<StudyPresenceSession> presenceSession
    ) {
        given(studyBreakSessionRepository.findActiveReconciliationCandidates())
                .willReturn(List.of(new StudyBreakReconciliationCandidate(
                        BREAK_SESSION_ID,
                        PRESENCE_SESSION_ID
                )));
        given(studyPresenceSessionRepository.findByIdForUpdate(PRESENCE_SESSION_ID))
                .willReturn(presenceSession);
        given(studyBreakSessionRepository.findActiveByIdForUpdate(BREAK_SESSION_ID))
                .willReturn(Optional.of(breakSession));
    }

    private StudyPresenceSession newActivePresence(Long id, Instant checkedInAt) {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(MEMBER_ID, 2L, checkedInAt);
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private void assertClosed(
            StudyBreakSession session,
            Instant expectedEndedAt,
            StudyBreakEndReason expectedEndReason
    ) {
        assertThat(session.getEndedAt()).isEqualTo(expectedEndedAt);
        assertThat(session.getEndReason()).isEqualTo(expectedEndReason);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }
}
