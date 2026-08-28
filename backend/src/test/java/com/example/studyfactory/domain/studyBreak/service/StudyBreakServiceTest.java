package com.example.studyfactory.domain.studyBreak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakStartBlockReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakEndReason;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.exception.StudyBreakException;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("휴식시간 공부 서비스 테스트")
class StudyBreakServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long PRESENCE_SESSION_ID = 11L;
    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);
    private static final Instant PRESENCE_STARTED_AT = Instant.parse("2026-08-28T00:00:00Z");
    private static final Instant AFTER_FIRST_STARTED_AT = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant AFTER_FIRST_ENDED_AT = Instant.parse("2026-08-28T01:45:00Z");
    private static final Instant START_AT = Instant.parse("2026-08-28T01:35:00Z");
    private static final Instant STOP_AT = Instant.parse("2026-08-28T01:40:00Z");

    @Mock
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Clock clock;

    private StudyBreakService studyBreakService;

    @BeforeEach
    void setUp() {
        studyBreakService = new StudyBreakService(
                studyBreakSessionRepository,
                studyPresenceSessionRepository,
                memberRepository,
                new StudyBreakWindowPolicy(),
                new StudyPresenceAutoClosePolicy(true),
                clock
        );
    }

    @Test
    @DisplayName("서버 시각의 현재 휴식 구간과 입실 기록으로 새 세션을 시작한다")
    void startFromCurrentWindowAndPresence() {
        Member member = member();
        StudyPresenceSession presence = activePresence();
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(presence));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());
        given(clock.instant()).willReturn(START_AT);
        given(studyBreakSessionRepository.save(any(StudyBreakSession.class)))
                .willAnswer(invocation -> withId(invocation.getArgument(0), 21L));

        var response = studyBreakService.start(MEMBER_ID);

        assertThat(response.changed()).isTrue();
        assertThat(response.status().checkedIn()).isTrue();
        assertThat(response.status().currentBreak().studyBreak()).isEqualTo(StudyBreak.AFTER_FIRST);
        assertThat(response.status().active()).isTrue();
        assertThat(response.status().canStart()).isFalse();
        assertThat(response.status().canStop()).isTrue();
        assertThat(response.status().startBlockReason()).isEqualTo(StudyBreakStartBlockReason.ALREADY_ACTIVE);
        assertThat(response.status().session()).satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo(21L);
            assertThat(session.presenceSessionId()).isEqualTo(PRESENCE_SESSION_ID);
            assertThat(session.branchId()).isEqualTo(BRANCH_ID);
            assertThat(session.studyDate()).isEqualTo(STUDY_DATE);
            assertThat(session.studyBreak()).isEqualTo(StudyBreak.AFTER_FIRST);
            assertThat(session.windowStartedAt()).isEqualTo(AFTER_FIRST_STARTED_AT);
            assertThat(session.windowEndedAt()).isEqualTo(AFTER_FIRST_ENDED_AT);
            assertThat(session.startedAt()).isEqualTo(START_AT);
            assertThat(session.active()).isTrue();
        });
        then(studyBreakSessionRepository).should().save(any(StudyBreakSession.class));
        then(studyBreakSessionRepository).should(never()).flush();
    }

    @Test
    @DisplayName("같은 입실과 휴식 구간의 시작 재요청은 기존 세션을 반환한다")
    void makeDuplicateStartIdempotent() {
        StudyPresenceSession presence = activePresence();
        StudyBreakSession activeBreak = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        withId(activeBreak, 21L);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(presence));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activeBreak));
        given(clock.instant()).willReturn(STOP_AT);

        var response = studyBreakService.start(MEMBER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.status().active()).isTrue();
        assertThat(response.status().session().sessionId()).isEqualTo(21L);
        then(studyBreakSessionRepository).should(never()).save(any(StudyBreakSession.class));
        then(studyBreakSessionRepository).should(never()).flush();
    }

    @Test
    @DisplayName("입실 중이 아니면 휴식시간 공부를 시작할 수 없다")
    void rejectStartWithoutPresence() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());
        given(clock.instant()).willReturn(START_AT);

        assertThatThrownBy(() -> studyBreakService.start(MEMBER_ID))
                .isInstanceOf(StudyBreakException.class)
                .hasMessageContaining("입실 중일 때만 휴식시간 공부를 시작할 수 있습니다.");

        then(studyBreakSessionRepository).should(never()).save(any(StudyBreakSession.class));
    }

    @Test
    @DisplayName("휴식 구간 밖에서는 입실 중이어도 시작할 수 없다")
    void rejectStartOutsideBreakWindow() {
        Instant duringSecondPeriod = Instant.parse("2026-08-28T02:00:00Z");
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activePresence()));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());
        given(clock.instant()).willReturn(duringSecondPeriod);

        assertThatThrownBy(() -> studyBreakService.start(MEMBER_ID))
                .isInstanceOf(StudyBreakException.class)
                .hasMessageContaining("현재는 휴식시간이 아닙니다.");

        then(studyBreakSessionRepository).should(never()).save(any(StudyBreakSession.class));
    }

    @Test
    @DisplayName("중지는 활성 세션을 회원 중지로 닫고 재요청은 변경하지 않는다")
    void stopAndMakeRepeatedStopIdempotent() {
        StudyPresenceSession presence = activePresence();
        StudyBreakSession activeBreak = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        withId(activeBreak, 21L);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(presence));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activeBreak), Optional.empty());
        given(clock.instant()).willReturn(STOP_AT, STOP_AT.plusSeconds(1));

        var firstResponse = studyBreakService.stop(MEMBER_ID);
        var repeatedResponse = studyBreakService.stop(MEMBER_ID);

        assertThat(firstResponse.changed()).isTrue();
        assertThat(firstResponse.status().active()).isFalse();
        assertThat(firstResponse.status().canStop()).isFalse();
        assertThat(firstResponse.status().canStart()).isTrue();
        assertThat(activeBreak.getEndedAt()).isEqualTo(STOP_AT);
        assertThat(activeBreak.getEndReason()).isEqualTo(StudyBreakEndReason.MEMBER_STOP);
        assertThat(repeatedResponse.changed()).isFalse();
        assertThat(repeatedResponse.status().active()).isFalse();
        then(studyBreakSessionRepository).should(never()).save(any(StudyBreakSession.class));
    }

    @Test
    @DisplayName("이전 휴식의 미종료 세션을 경계로 닫고 플러시한 뒤 현재 휴식을 시작한다")
    void reconcileStaleEarlierBreakFlushAndStartCurrentBreak() {
        Instant duringLunch = Instant.parse("2026-08-28T03:10:00Z");
        StudyBreakSession staleAfterFirst = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        withId(staleAfterFirst, 21L);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activePresence()));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(staleAfterFirst));
        given(clock.instant()).willReturn(duringLunch);
        given(studyBreakSessionRepository.save(any(StudyBreakSession.class)))
                .willAnswer(invocation -> withId(invocation.getArgument(0), 22L));

        var response = studyBreakService.start(MEMBER_ID);

        assertThat(staleAfterFirst.getEndedAt()).isEqualTo(AFTER_FIRST_ENDED_AT);
        assertThat(staleAfterFirst.getEndReason()).isEqualTo(StudyBreakEndReason.BREAK_ENDED);
        assertThat(response.changed()).isTrue();
        assertThat(response.status().session()).satisfies(session -> {
            assertThat(session.sessionId()).isEqualTo(22L);
            assertThat(session.studyBreak()).isEqualTo(StudyBreak.LUNCH);
            assertThat(session.startedAt()).isEqualTo(duringLunch);
            assertThat(session.windowStartedAt()).isEqualTo(Instant.parse("2026-08-28T03:05:00Z"));
            assertThat(session.windowEndedAt()).isEqualTo(Instant.parse("2026-08-28T04:20:00Z"));
        });
        InOrder writes = inOrder(studyBreakSessionRepository);
        writes.verify(studyBreakSessionRepository).flush();
        writes.verify(studyBreakSessionRepository).save(any(StudyBreakSession.class));
    }

    @Test
    @DisplayName("휴식 종료 경계의 중지는 회원 중지가 아닌 휴식 종료로 정규화한다")
    void normalizeStopAtExactBreakEnd() {
        StudyBreakSession activeBreak = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activePresence()));
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activeBreak));
        given(clock.instant()).willReturn(AFTER_FIRST_ENDED_AT);

        var response = studyBreakService.stop(MEMBER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(response.status().active()).isFalse();
        assertThat(activeBreak.getEndedAt()).isEqualTo(AFTER_FIRST_ENDED_AT);
        assertThat(activeBreak.getEndReason()).isEqualTo(StudyBreakEndReason.BREAK_ENDED);
    }

    @Test
    @DisplayName("늦은 복구 요청도 더 이른 실제 퇴실 시각을 휴식 종료보다 우선한다")
    void preferEarlierPresenceEndDuringLateCommandReconciliation() {
        Instant checkedOutAt = Instant.parse("2026-08-28T01:40:00Z");
        Instant reconcileAt = Instant.parse("2026-08-28T01:50:00Z");
        StudyPresenceSession closedPresence = activePresence();
        closedPresence.checkOut(checkedOutAt);
        StudyBreakSession activeBreak = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        given(memberRepository.findByIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.empty());
        given(studyBreakSessionRepository.findActiveByMemberIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(activeBreak));
        given(studyPresenceSessionRepository.findById(PRESENCE_SESSION_ID))
                .willReturn(Optional.of(closedPresence));
        given(clock.instant()).willReturn(reconcileAt);

        var response = studyBreakService.stop(MEMBER_ID);

        assertThat(response.changed()).isFalse();
        assertThat(activeBreak.getEndedAt()).isEqualTo(checkedOutAt);
        assertThat(activeBreak.getEndReason()).isEqualTo(StudyBreakEndReason.PRESENCE_ENDED);
    }

    @Test
    @DisplayName("저장 조정 전에도 논리적으로 종료된 입실과 휴식 세션을 현재 상태에서 숨긴다")
    void hideLogicallyStalePresenceAndBreakFromStatus() {
        Instant afterSeoulMidnight = Instant.parse("2026-08-28T15:00:05Z");
        StudyPresenceSession stalePresence = activePresence();
        StudyBreakSession staleBreak = activeAfterFirst(PRESENCE_SESSION_ID, START_AT);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));
        given(studyPresenceSessionRepository.findByActiveMemberId(MEMBER_ID))
                .willReturn(Optional.of(stalePresence));
        given(studyBreakSessionRepository.findByActiveMemberId(MEMBER_ID))
                .willReturn(Optional.of(staleBreak));
        given(clock.instant()).willReturn(afterSeoulMidnight);

        var response = studyBreakService.findStatus(MEMBER_ID);

        assertThat(response.checkedIn()).isFalse();
        assertThat(response.active()).isFalse();
        assertThat(response.session()).isNull();
        assertThat(response.canStart()).isFalse();
        assertThat(response.canStop()).isFalse();
        assertThat(response.startBlockReason()).isEqualTo(StudyBreakStartBlockReason.NOT_CHECKED_IN);
    }

    private Member member() {
        Member member = new Member(
                BRANCH_ID,
                "김회원",
                "password",
                10,
                LocalDate.of(2026, 8, 1),
                3L
        );
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private StudyPresenceSession activePresence() {
        StudyPresenceSession presence = new StudyPresenceSession(
                MEMBER_ID,
                BRANCH_ID,
                PRESENCE_STARTED_AT
        );
        ReflectionTestUtils.setField(presence, "id", PRESENCE_SESSION_ID);
        return presence;
    }

    private StudyBreakSession activeAfterFirst(Long presenceSessionId, Instant startedAt) {
        return new StudyBreakSession(
                presenceSessionId,
                MEMBER_ID,
                BRANCH_ID,
                STUDY_DATE,
                StudyBreak.AFTER_FIRST,
                AFTER_FIRST_STARTED_AT,
                AFTER_FIRST_ENDED_AT,
                startedAt
        );
    }

    private StudyBreakSession withId(StudyBreakSession session, Long id) {
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }
}
