package com.example.studyfactory.domain.studyBreak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴식시간 공부 세션 생명주기 서비스 테스트")
class StudyBreakLifecycleServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PRESENCE_SESSION_ID = 11L;
    private static final Instant WINDOW_STARTED_AT = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant WINDOW_ENDED_AT = Instant.parse("2026-08-28T01:45:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-08-28T01:35:00Z");

    @Mock
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Test
    @DisplayName("현재 입실 기록과 일치하는 활성 휴식 세션을 퇴실 시각에 종료한다")
    void closeMatchingActiveSessionAtPresenceEnd() {
        StudyBreakSession session = newSession(PRESENCE_SESSION_ID);
        Instant presenceEndedAt = Instant.parse("2026-08-28T01:40:00Z");
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(session));

        service().closeForPresenceEnd(MEMBER_ID, PRESENCE_SESSION_ID, presenceEndedAt);

        assertClosed(session, presenceEndedAt, StudyBreakEndReason.PRESENCE_ENDED);
    }

    @Test
    @DisplayName("퇴실 시각을 휴식 세션 시작과 종료 경계 안으로 제한한다")
    void capPresenceEndToSessionBoundaries() {
        StudyBreakSession beforeStart = newSession(PRESENCE_SESSION_ID);
        StudyBreakSession afterBreak = newSession(PRESENCE_SESSION_ID);
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(beforeStart), Optional.of(afterBreak));

        StudyBreakLifecycleService service = service();
        service.closeForPresenceEnd(MEMBER_ID, PRESENCE_SESSION_ID, STARTED_AT.minusSeconds(30));
        service.closeForPresenceEnd(MEMBER_ID, PRESENCE_SESSION_ID, WINDOW_ENDED_AT.plusSeconds(30));

        assertClosed(beforeStart, STARTED_AT, StudyBreakEndReason.PRESENCE_ENDED);
        assertClosed(afterBreak, WINDOW_ENDED_AT, StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("다른 입실 기록에 속한 휴식 세션은 종료하지 않는다")
    void ignoreSessionFromDifferentPresence() {
        StudyBreakSession session = newSession(PRESENCE_SESSION_ID);
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(session));

        service().closeForPresenceEnd(MEMBER_ID, 99L, Instant.parse("2026-08-28T01:40:00Z"));

        assertThat(session.isActive()).isTrue();
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getEndReason()).isNull();
    }

    @Test
    @DisplayName("이미 종료된 후 재시도하면 활성 조회가 비어 있어 멱등하다")
    void remainIdempotentWhenPresenceEndIsRetried() {
        StudyBreakSession session = newSession(PRESENCE_SESSION_ID);
        Instant presenceEndedAt = Instant.parse("2026-08-28T01:40:00Z");
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(session), Optional.empty());
        StudyBreakLifecycleService service = service();

        service.closeForPresenceEnd(MEMBER_ID, PRESENCE_SESSION_ID, presenceEndedAt);

        assertThatCode(() -> service.closeForPresenceEnd(MEMBER_ID, PRESENCE_SESSION_ID, presenceEndedAt))
                .doesNotThrowAnyException();
        assertClosed(session, presenceEndedAt, StudyBreakEndReason.PRESENCE_ENDED);
        then(studyBreakSessionRepository).should(org.mockito.Mockito.times(2))
                .findActiveByMemberIdForUpdate(MEMBER_ID);
    }

    private StudyBreakLifecycleService service() {
        return new StudyBreakLifecycleService(studyBreakSessionRepository);
    }

    private StudyBreakSession newSession(Long presenceSessionId) {
        return new StudyBreakSession(
                presenceSessionId,
                MEMBER_ID,
                2L,
                LocalDate.of(2026, 8, 28),
                StudyBreak.AFTER_FIRST,
                WINDOW_STARTED_AT,
                WINDOW_ENDED_AT,
                STARTED_AT
        );
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
