package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("학습실 자정 자동 퇴실 정책 테스트")
class StudyPresenceAutoClosePolicyTest {

    private final StudyPresenceAutoClosePolicy policy = new StudyPresenceAutoClosePolicy(true);

    @Test
    @DisplayName("현재 시각의 서울 날짜 시작 시각을 UTC Instant로 계산한다")
    void calculateCurrentSeoulDayStart() {
        Instant now = Instant.parse("2026-08-28T15:00:05Z");

        Instant dayStartedAt = policy.currentSeoulDayStartedAt(now);

        assertThat(dayStartedAt).isEqualTo(Instant.parse("2026-08-28T15:00:00Z"));
    }

    @Test
    @DisplayName("입실한 서울 날짜의 다음 자정을 자동 퇴실 경계로 계산한다")
    void calculateFirstMidnightAfterCheckIn() {
        Instant checkedInAt = Instant.parse("2026-08-25T03:00:00Z");

        Instant midnight = policy.firstMidnightAfter(checkedInAt);

        assertThat(midnight).isEqualTo(Instant.parse("2026-08-25T15:00:00Z"));
    }

    @Test
    @DisplayName("자정 직전에는 유지하고 정확히 자정부터 자동 종료 대상으로 본다")
    void applyExactMidnightBoundary() {
        StudyPresenceSession session = new StudyPresenceSession(
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );

        assertThat(policy.shouldAutomaticallyClose(
                session,
                Instant.parse("2026-08-28T14:59:59.999999999Z")
        )).isFalse();
        assertThat(policy.shouldAutomaticallyClose(
                session,
                Instant.parse("2026-08-28T15:00:00Z")
        )).isTrue();
    }

    @Test
    @DisplayName("이미 닫힌 기록은 자동 종료 대상으로 다시 보지 않는다")
    void ignoreClosedSession() {
        StudyPresenceSession session = new StudyPresenceSession(
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        session.checkOut(Instant.parse("2026-08-28T14:30:00Z"));

        assertThat(policy.shouldAutomaticallyClose(
                session,
                Instant.parse("2026-08-28T15:00:00Z")
        )).isFalse();
    }

    @Test
    @DisplayName("기능이 비활성화되면 자정이 지나도 자동 종료하지 않는다")
    void keepSessionWhenPolicyIsDisabled() {
        StudyPresenceAutoClosePolicy disabledPolicy = new StudyPresenceAutoClosePolicy(false);
        StudyPresenceSession session = new StudyPresenceSession(
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );

        assertThat(disabledPolicy.automaticallyCloseIfStale(
                session,
                Instant.parse("2026-08-28T15:00:00Z")
        )).isFalse();
        assertThat(session.isActive()).isTrue();
        assertThat(session.isAutomaticallyClosed()).isFalse();
    }
}
