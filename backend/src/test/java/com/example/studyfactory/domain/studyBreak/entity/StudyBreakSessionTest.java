package com.example.studyfactory.domain.studyBreak.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("휴식시간 공부 세션 엔티티 테스트")
class StudyBreakSessionTest {

    private static final Long PRESENCE_SESSION_ID = 11L;
    private static final Long MEMBER_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);
    private static final Instant WINDOW_STARTED_AT = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant WINDOW_ENDED_AT = Instant.parse("2026-08-28T01:45:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-08-28T01:35:00Z");

    @Test
    @DisplayName("휴식시간 안에서 시작한 세션은 회원별 활성 상태로 생성된다")
    void createActiveSessionInsideBreakWindow() {
        StudyBreakSession session = newSession(STARTED_AT);

        assertThat(session.getPresenceSessionId()).isEqualTo(PRESENCE_SESSION_ID);
        assertThat(session.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(session.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(session.getStudyDate()).isEqualTo(STUDY_DATE);
        assertThat(session.getStudyBreak()).isEqualTo(StudyBreak.AFTER_FIRST);
        assertThat(session.getWindowStartedAt()).isEqualTo(WINDOW_STARTED_AT);
        assertThat(session.getWindowEndedAt()).isEqualTo(WINDOW_ENDED_AT);
        assertThat(session.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getEndReason()).isNull();
        assertThat(session.getActiveMemberId()).isEqualTo(MEMBER_ID);
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("휴식 시작 경계는 포함하고 종료 경계는 포함하지 않는다")
    void enforceHalfOpenStartBoundary() {
        assertThat(newSession(WINDOW_STARTED_AT).isActive()).isTrue();
        assertThat(newSession(WINDOW_ENDED_AT.minusNanos(1)).isActive()).isTrue();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> newSession(WINDOW_STARTED_AT.minusNanos(1)))
                .withMessage("Break study must start inside its break window");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> newSession(WINDOW_ENDED_AT))
                .withMessage("Break study must start inside its break window");
    }

    @Test
    @DisplayName("생성자는 필수값과 올바른 휴식 구간을 검증한다")
    void validateRequiredFieldsAndWindowOrder() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StudyBreakSession(
                        null,
                        MEMBER_ID,
                        BRANCH_ID,
                        STUDY_DATE,
                        StudyBreak.AFTER_FIRST,
                        WINDOW_STARTED_AT,
                        WINDOW_ENDED_AT,
                        STARTED_AT
                ))
                .withMessage("presenceSessionId must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> new StudyBreakSession(
                        PRESENCE_SESSION_ID,
                        MEMBER_ID,
                        BRANCH_ID,
                        STUDY_DATE,
                        null,
                        WINDOW_STARTED_AT,
                        WINDOW_ENDED_AT,
                        STARTED_AT
                ))
                .withMessage("studyBreak must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StudyBreakSession(
                        PRESENCE_SESSION_ID,
                        MEMBER_ID,
                        BRANCH_ID,
                        STUDY_DATE,
                        StudyBreak.AFTER_FIRST,
                        WINDOW_STARTED_AT,
                        WINDOW_STARTED_AT,
                        WINDOW_STARTED_AT
                ))
                .withMessage("Break window end must be after its start");
    }

    @Test
    @DisplayName("회원이 휴식 종료 전에 중지하면 요청 시각과 회원 중지 사유를 남긴다")
    void stopByMemberBeforeBreakEnd() {
        StudyBreakSession session = newSession(STARTED_AT);
        Instant stoppedAt = Instant.parse("2026-08-28T01:40:00Z");

        session.stopByMember(stoppedAt);

        assertClosed(session, stoppedAt, StudyBreakEndReason.MEMBER_STOP);
    }

    @Test
    @DisplayName("회원 중지 시각은 시작 이후와 휴식 종료 이전으로 제한한다")
    void capMemberStopToSessionAndBreakBoundaries() {
        StudyBreakSession beforeStart = newSession(STARTED_AT);
        StudyBreakSession exactlyAtBreakEnd = newSession(STARTED_AT);
        StudyBreakSession afterBreakEnd = newSession(STARTED_AT);

        beforeStart.stopByMember(STARTED_AT.minusSeconds(30));
        exactlyAtBreakEnd.stopByMember(WINDOW_ENDED_AT);
        afterBreakEnd.stopByMember(WINDOW_ENDED_AT.plusSeconds(30));

        assertClosed(beforeStart, STARTED_AT, StudyBreakEndReason.MEMBER_STOP);
        assertClosed(exactlyAtBreakEnd, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
        assertClosed(afterBreakEnd, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("휴식 종료 자동 처리는 경계 시각에서 한 번만 적용된다")
    void automaticallyEndAtBreakBoundaryIdempotently() {
        StudyBreakSession session = newSession(STARTED_AT);

        assertThat(session.endAtBreakBoundaryIfExpired(WINDOW_ENDED_AT.minusNanos(1))).isFalse();
        assertThat(session.isActive()).isTrue();
        assertThat(session.endAtBreakBoundaryIfExpired(WINDOW_ENDED_AT)).isTrue();
        assertClosed(session, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);

        assertThat(session.endAtBreakBoundaryIfExpired(WINDOW_ENDED_AT.plusSeconds(1))).isFalse();
        assertClosed(session, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("퇴실 시각은 세션 시작과 휴식 종료 경계로 제한하고 종료 사유를 구분한다")
    void endForPresenceWithCorrectCapsAndReasons() {
        StudyBreakSession beforeStart = newSession(STARTED_AT);
        StudyBreakSession duringBreak = newSession(STARTED_AT);
        StudyBreakSession atBreakEnd = newSession(STARTED_AT);
        StudyBreakSession afterBreakEnd = newSession(STARTED_AT);
        Instant presenceEndedDuringBreak = Instant.parse("2026-08-28T01:41:00Z");

        beforeStart.endForPresence(STARTED_AT.minusSeconds(30));
        duringBreak.endForPresence(presenceEndedDuringBreak);
        atBreakEnd.endForPresence(WINDOW_ENDED_AT);
        afterBreakEnd.endForPresence(WINDOW_ENDED_AT.plusSeconds(30));

        assertClosed(beforeStart, STARTED_AT, StudyBreakEndReason.PRESENCE_ENDED);
        assertClosed(duringBreak, presenceEndedDuringBreak, StudyBreakEndReason.PRESENCE_ENDED);
        assertClosed(atBreakEnd, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
        assertClosed(afterBreakEnd, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("종료된 세션은 다른 종료 요청으로 덮어쓸 수 없다")
    void rejectOverwritingTerminalState() {
        StudyBreakSession session = newSession(STARTED_AT);
        Instant stoppedAt = Instant.parse("2026-08-28T01:40:00Z");
        session.stopByMember(stoppedAt);

        assertThatIllegalStateException()
                .isThrownBy(() -> session.endForPresence(WINDOW_ENDED_AT))
                .withMessage("Break study session is already closed");
        assertClosed(session, stoppedAt, StudyBreakEndReason.MEMBER_STOP);
    }

    private StudyBreakSession newSession(Instant startedAt) {
        return new StudyBreakSession(
                PRESENCE_SESSION_ID,
                MEMBER_ID,
                BRANCH_ID,
                STUDY_DATE,
                StudyBreak.AFTER_FIRST,
                WINDOW_STARTED_AT,
                WINDOW_ENDED_AT,
                startedAt
        );
    }

    private void assertClosed(
            StudyBreakSession session,
            Instant expectedEndedAt,
            StudyBreakEndReason expectedReason
    ) {
        assertThat(session.getEndedAt()).isEqualTo(expectedEndedAt);
        assertThat(session.getEndReason()).isEqualTo(expectedReason);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }
}
