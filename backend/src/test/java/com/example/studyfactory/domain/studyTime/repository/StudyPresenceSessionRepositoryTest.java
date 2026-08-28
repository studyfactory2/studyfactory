package com.example.studyfactory.domain.studyTime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.studyTime.entity.StudyPresenceSession;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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

    private StudyPresenceSession closedSession(Long memberId, Instant checkedInAt, Instant checkedOutAt) {
        StudyPresenceSession session = new StudyPresenceSession(memberId, 2L, checkedInAt);
        session.checkOut(checkedOutAt);
        return session;
    }
}
