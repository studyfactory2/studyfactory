package com.example.studyfactory.domain.studyPresence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@DisplayName("학습실 입퇴실 기록 저장소 테스트")
class StudyPresenceSessionRepositoryTest {

    private static final Instant WINDOW_START = Instant.parse("2026-08-28T01:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-28T03:00:00Z");

    @Autowired
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Test
    @DisplayName("한 회원에게 두 개의 활성 입실 기록을 저장할 수 없다")
    void rejectTwoActiveSessionsForSameMember() {
        studyPresenceSessionRepository.saveAndFlush(new StudyPresenceSession(1L, 2L, WINDOW_START));

        assertThatThrownBy(() -> studyPresenceSessionRepository.saveAndFlush(
                new StudyPresenceSession(1L, 2L, WINDOW_START.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 회원의 종료된 기록은 여러 개 저장할 수 있다")
    void allowMultipleClosedSessionsForSameMember() {
        StudyPresenceSession first = closedSession(
                1L,
                WINDOW_START.minusSeconds(3600),
                WINDOW_START.minusSeconds(1800)
        );
        StudyPresenceSession second = closedSession(
                1L,
                WINDOW_START,
                WINDOW_START.plusSeconds(1800)
        );

        studyPresenceSessionRepository.saveAllAndFlush(List.of(first, second));

        assertThat(studyPresenceSessionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("서로 다른 회원은 각각 활성 입실 기록을 가질 수 있다")
    void allowActiveSessionsForDifferentMembers() {
        studyPresenceSessionRepository.saveAllAndFlush(List.of(
                new StudyPresenceSession(1L, 2L, WINDOW_START),
                new StudyPresenceSession(2L, 2L, WINDOW_START)
        ));

        assertThat(studyPresenceSessionRepository.findByActiveMemberId(1L)).isPresent();
        assertThat(studyPresenceSessionRepository.findByActiveMemberId(2L)).isPresent();
    }

    @Test
    @DisplayName("입실 기록 ID 조회는 비관적 쓰기 잠금을 사용한다")
    void findPresenceByIdForUpdate() throws NoSuchMethodException {
        StudyPresenceSession session = studyPresenceSessionRepository.saveAndFlush(
                new StudyPresenceSession(1L, 2L, WINDOW_START)
        );

        assertThat(studyPresenceSessionRepository.findByIdForUpdate(session.getId()))
                .contains(session);

        Lock lock = StudyPresenceSessionRepository.class
                .getMethod("findByIdForUpdate", Long.class)
                .getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("활성 키와 퇴실 시간의 불일치 상태를 저장할 수 없다")
    void rejectInconsistentActiveState() {
        StudyPresenceSession session = studyPresenceSessionRepository.saveAndFlush(
                new StudyPresenceSession(1L, 2L, WINDOW_START)
        );
        ReflectionTestUtils.setField(session, "activeMemberId", null);

        assertThatThrownBy(studyPresenceSessionRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("조회 구간과 실제로 겹치는 회원 입실 기록만 시간순으로 찾는다")
    void findOverlappingSessions() {
        StudyPresenceSession endsAtWindowStart = closedSession(
                1L,
                WINDOW_START.minusSeconds(3600),
                WINDOW_START
        );
        StudyPresenceSession crossesWindowStart = closedSession(
                1L,
                WINDOW_START.minusSeconds(1800),
                WINDOW_START.plusSeconds(1800)
        );
        StudyPresenceSession openInsideWindow = new StudyPresenceSession(
                1L,
                2L,
                WINDOW_START.plusSeconds(3600)
        );
        StudyPresenceSession startsAtWindowEnd = closedSession(
                1L,
                WINDOW_END,
                WINDOW_END.plusSeconds(1800)
        );
        StudyPresenceSession otherMember = new StudyPresenceSession(
                2L,
                2L,
                WINDOW_START.plusSeconds(60)
        );
        studyPresenceSessionRepository.saveAllAndFlush(List.of(
                endsAtWindowStart,
                crossesWindowStart,
                openInsideWindow,
                startsAtWindowEnd,
                otherMember
        ));

        List<StudyPresenceSession> sessions = studyPresenceSessionRepository.findOverlappingByMemberId(
                1L,
                WINDOW_START,
                WINDOW_END
        );

        assertThat(sessions).containsExactly(crossesWindowStart, openInsideWindow);
    }

    @Test
    @DisplayName("지점의 활성 입실 기록만 입실 시간순으로 찾는다")
    void findActiveSessionsByBranch() {
        StudyPresenceSession first = new StudyPresenceSession(
                1L,
                2L,
                WINDOW_START.minusSeconds(60)
        );
        StudyPresenceSession second = new StudyPresenceSession(2L, 2L, WINDOW_START);
        StudyPresenceSession otherBranch = new StudyPresenceSession(3L, 3L, WINDOW_START);
        StudyPresenceSession closed = closedSession(
                4L,
                WINDOW_START.minusSeconds(120),
                WINDOW_START.minusSeconds(60)
        );
        studyPresenceSessionRepository.saveAllAndFlush(List.of(second, otherBranch, closed, first));

        List<StudyPresenceSession> sessions = studyPresenceSessionRepository.findActiveByBranchId(2L);

        assertThat(sessions).containsExactly(first, second);
    }

    @Test
    @DisplayName("현재 서울 날짜 이전에 입실한 활성 기록만 자동 퇴실 대상으로 잠금 조회한다")
    void findStaleActiveSessionsForUpdate() {
        Instant currentSeoulDayStartedAt = Instant.parse("2026-08-28T15:00:00Z");
        StudyPresenceSession firstStale = new StudyPresenceSession(
                1L,
                2L,
                currentSeoulDayStartedAt.minusSeconds(3600)
        );
        StudyPresenceSession secondStale = new StudyPresenceSession(
                2L,
                3L,
                currentSeoulDayStartedAt.minusSeconds(1)
        );
        StudyPresenceSession exactlyAtBoundary = new StudyPresenceSession(
                3L,
                2L,
                currentSeoulDayStartedAt
        );
        StudyPresenceSession alreadyClosed = closedSession(
                4L,
                currentSeoulDayStartedAt.minusSeconds(7200),
                currentSeoulDayStartedAt.minusSeconds(60)
        );
        studyPresenceSessionRepository.saveAllAndFlush(List.of(
                exactlyAtBoundary,
                secondStale,
                alreadyClosed,
                firstStale
        ));

        List<StudyPresenceSession> sessions =
                studyPresenceSessionRepository.findStaleActiveSessionsForUpdate(currentSeoulDayStartedAt);

        assertThat(sessions).containsExactly(firstStale, secondStale);
    }

    @Test
    @DisplayName("지점과 회원이 모두 일치하며 조회 구간과 겹치는 이력만 찾는다")
    void findOverlappingSessionsByBranchAndMember() {
        StudyPresenceSession matching = closedSession(
                1L,
                WINDOW_START.minusSeconds(60),
                WINDOW_START.plusSeconds(60)
        );
        StudyPresenceSession otherMember = closedSession(
                2L,
                WINDOW_START,
                WINDOW_START.plusSeconds(60)
        );
        StudyPresenceSession otherBranch = new StudyPresenceSession(1L, 3L, WINDOW_START);
        otherBranch.checkOut(WINDOW_START.plusSeconds(60));
        studyPresenceSessionRepository.saveAllAndFlush(List.of(matching, otherMember, otherBranch));

        List<StudyPresenceSession> sessions =
                studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                        2L,
                        1L,
                        WINDOW_START,
                        WINDOW_END
                );

        assertThat(sessions).containsExactly(matching);
    }

    private StudyPresenceSession closedSession(Long memberId, Instant checkedInAt, Instant checkedOutAt) {
        StudyPresenceSession session = new StudyPresenceSession(memberId, 2L, checkedInAt);
        session.checkOut(checkedOutAt);
        return session;
    }
}
