package com.example.studyfactory.domain.studyBreak.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakReconciliationCandidate;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@DisplayName("휴식시간 공부 세션 저장소 테스트")
class StudyBreakSessionRepositoryTest {

    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);
    private static final Instant BREAK_STARTED_AT = Instant.parse("2026-08-28T01:30:00Z");
    private static final Instant BREAK_ENDED_AT = Instant.parse("2026-08-28T01:45:00Z");
    private static final Instant QUERY_STARTED_AT = Instant.parse("2026-08-28T01:35:00Z");
    private static final Instant QUERY_ENDED_AT = Instant.parse("2026-08-28T01:43:00Z");

    @Autowired
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Test
    @DisplayName("한 회원에게 두 개의 활성 휴식시간 공부 세션을 저장할 수 없다")
    void rejectTwoActiveSessionsForSameMember() {
        studyBreakSessionRepository.saveAndFlush(session(
                101L,
                1L,
                BREAK_STARTED_AT.plusSeconds(60)
        ));

        assertThatThrownBy(() -> studyBreakSessionRepository.saveAndFlush(session(
                102L,
                1L,
                BREAK_STARTED_AT.plusSeconds(120)
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 회원과 휴식시간의 종료된 세션은 여러 개 저장할 수 있다")
    void allowMultipleClosedSessionsForSameMemberAndBreak() {
        StudyBreakSession first = closedSession(
                101L,
                1L,
                BREAK_STARTED_AT.plusSeconds(60),
                BREAK_STARTED_AT.plusSeconds(120)
        );
        StudyBreakSession second = closedSession(
                102L,
                1L,
                BREAK_STARTED_AT.plusSeconds(180),
                BREAK_STARTED_AT.plusSeconds(240)
        );

        studyBreakSessionRepository.saveAllAndFlush(List.of(first, second));

        assertThat(studyBreakSessionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("활성 세션을 정렬해 찾고 개별 세션을 비관적 쓰기 잠금으로 조회한다")
    void findActiveSessionForUpdate() throws NoSuchMethodException {
        StudyBreakSession expected = session(
                101L,
                1L,
                BREAK_STARTED_AT.plusSeconds(60)
        );
        StudyBreakSession otherMember = session(
                102L,
                2L,
                BREAK_STARTED_AT.plusSeconds(120)
        );
        studyBreakSessionRepository.saveAllAndFlush(List.of(otherMember, expected));

        assertThat(studyBreakSessionRepository.findActiveByMemberIdForUpdate(1L))
                .contains(expected);
        assertThat(studyBreakSessionRepository.findActiveByIdForUpdate(expected.getId()))
                .contains(expected);
        assertThat(studyBreakSessionRepository.findActiveReconciliationCandidates())
                .extracting(StudyBreakReconciliationCandidate::breakSessionId)
                .containsExactly(otherMember.getId(), expected.getId());

        Lock memberLock = StudyBreakSessionRepository.class
                .getMethod("findActiveByMemberIdForUpdate", Long.class)
                .getAnnotation(Lock.class);
        Lock sessionLock = StudyBreakSessionRepository.class
                .getMethod("findActiveByIdForUpdate", Long.class)
                .getAnnotation(Lock.class);
        assertThat(memberLock).isNotNull();
        assertThat(memberLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(sessionLock).isNotNull();
        assertThat(sessionLock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("조회 구간과 겹치는 회원 세션만 반열린 구간 기준과 시작 시각순으로 찾는다")
    void findOverlappingSessionsByMemberWithHalfOpenBoundaries() {
        StudyBreakSession endsAtWindowStart = closedSession(
                101L,
                1L,
                BREAK_STARTED_AT.plusSeconds(60),
                QUERY_STARTED_AT
        );
        StudyBreakSession crossesWindowStart = closedSession(
                102L,
                1L,
                QUERY_STARTED_AT.minusSeconds(60),
                QUERY_STARTED_AT.plusSeconds(60)
        );
        StudyBreakSession closedInsideWindow = closedSession(
                103L,
                1L,
                QUERY_STARTED_AT.plusSeconds(120),
                QUERY_STARTED_AT.plusSeconds(180)
        );
        StudyBreakSession openInsideWindow = session(
                104L,
                1L,
                QUERY_STARTED_AT.plusSeconds(300)
        );
        StudyBreakSession startsAtWindowEnd = closedSession(
                105L,
                1L,
                QUERY_ENDED_AT,
                QUERY_ENDED_AT.plusSeconds(60)
        );
        StudyBreakSession otherMember = session(
                106L,
                2L,
                QUERY_STARTED_AT.plusSeconds(60)
        );
        studyBreakSessionRepository.saveAllAndFlush(List.of(
                startsAtWindowEnd,
                openInsideWindow,
                otherMember,
                endsAtWindowStart,
                closedInsideWindow,
                crossesWindowStart
        ));

        List<StudyBreakSession> sessions = studyBreakSessionRepository.findOverlappingByMemberId(
                1L,
                QUERY_STARTED_AT,
                QUERY_ENDED_AT
        );

        assertThat(sessions).containsExactly(
                crossesWindowStart,
                closedInsideWindow,
                openInsideWindow
        );
    }

    @Test
    @DisplayName("활성 키와 종료 상태가 불일치하는 세션을 저장할 수 없다")
    void rejectInconsistentActiveState() {
        StudyBreakSession session = studyBreakSessionRepository.saveAndFlush(session(
                101L,
                1L,
                BREAK_STARTED_AT.plusSeconds(60)
        ));
        ReflectionTestUtils.setField(session, "activeMemberId", null);

        assertThatThrownBy(studyBreakSessionRepository::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원 휴식 공부 구간 행을 지점과 무관하게 날짜 및 반열린 경계와 안정된 시간순으로 투영한다")
    void projectMemberIntervalRowsAcrossBranchesAndStudyDates() {
        StudyBreakSession endsAtWindowStart = closedSession(
                101L,
                1L,
                2L,
                STUDY_DATE,
                BREAK_STARTED_AT.plusSeconds(60),
                QUERY_STARTED_AT
        );
        StudyBreakSession crossesWindowStart = closedSession(
                102L,
                1L,
                3L,
                STUDY_DATE,
                QUERY_STARTED_AT.minusSeconds(60),
                QUERY_STARTED_AT.plusSeconds(60)
        );
        Instant tiedStartedAt = QUERY_STARTED_AT.plusSeconds(120);
        StudyBreakSession tiedFirst = closedSession(
                103L,
                1L,
                2L,
                STUDY_DATE,
                tiedStartedAt,
                tiedStartedAt.plusSeconds(30)
        );
        StudyBreakSession tiedSecond = closedSession(
                104L,
                1L,
                3L,
                STUDY_DATE,
                tiedStartedAt,
                tiedStartedAt.plusSeconds(60)
        );
        StudyBreakSession activeInsideWindow = session(
                105L,
                1L,
                4L,
                STUDY_DATE,
                QUERY_STARTED_AT.plusSeconds(300)
        );
        StudyBreakSession startsAtWindowEnd = closedSession(
                106L,
                1L,
                2L,
                STUDY_DATE,
                QUERY_ENDED_AT,
                QUERY_ENDED_AT.plusSeconds(30)
        );
        StudyBreakSession outsideStoredDate = closedSession(
                107L,
                1L,
                2L,
                STUDY_DATE.minusDays(1),
                QUERY_STARTED_AT.plusSeconds(30),
                QUERY_STARTED_AT.plusSeconds(90)
        );
        StudyBreakSession otherMember = closedSession(
                108L,
                2L,
                2L,
                STUDY_DATE,
                QUERY_STARTED_AT.plusSeconds(60),
                QUERY_STARTED_AT.plusSeconds(120)
        );
        studyBreakSessionRepository.saveAllAndFlush(List.of(
                startsAtWindowEnd,
                tiedFirst,
                otherMember,
                endsAtWindowStart,
                activeInsideWindow,
                outsideStoredDate,
                tiedSecond,
                crossesWindowStart
        ));

        List<StudyBreakIntervalRow> rows = studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                STUDY_DATE,
                QUERY_STARTED_AT,
                QUERY_ENDED_AT
        );

        assertThat(rows)
                .extracting(StudyBreakIntervalRow::sessionId)
                .containsExactly(
                        crossesWindowStart.getId(),
                        tiedFirst.getId(),
                        tiedSecond.getId(),
                        activeInsideWindow.getId()
                );
        assertThat(rows.getFirst()).isEqualTo(new StudyBreakIntervalRow(
                crossesWindowStart.getId(),
                102L,
                1L,
                3L,
                STUDY_DATE,
                StudyBreak.AFTER_FIRST,
                crossesWindowStart.getStartedAt(),
                crossesWindowStart.getEndedAt(),
                BREAK_ENDED_AT
        ));
        assertThat(rows.getLast().endedAt()).isNull();
        assertThat(rows.getLast().windowEndedAt()).isEqualTo(BREAK_ENDED_AT);

        assertThat(studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                STUDY_DATE,
                BREAK_ENDED_AT,
                BREAK_ENDED_AT.plusSeconds(60)
        )).isEmpty();
    }

    @Test
    @DisplayName("지점 회원 휴식 공부 구간 행은 다른 지점과 다른 회원을 제외한다")
    void projectBranchMemberIntervalRowsWithBranchIsolation() {
        StudyBreakSession matching = closedSession(
                201L,
                1L,
                2L,
                STUDY_DATE,
                QUERY_STARTED_AT.plusSeconds(60),
                QUERY_STARTED_AT.plusSeconds(120)
        );
        StudyBreakSession otherBranch = closedSession(
                202L,
                1L,
                3L,
                STUDY_DATE,
                QUERY_STARTED_AT.plusSeconds(180),
                QUERY_STARTED_AT.plusSeconds(240)
        );
        StudyBreakSession otherMember = closedSession(
                203L,
                2L,
                2L,
                STUDY_DATE,
                QUERY_STARTED_AT.plusSeconds(300),
                QUERY_STARTED_AT.plusSeconds(360)
        );
        studyBreakSessionRepository.saveAllAndFlush(List.of(
                otherBranch,
                otherMember,
                matching
        ));

        List<StudyBreakIntervalRow> rows =
                studyBreakSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                        2L,
                        1L,
                        STUDY_DATE,
                        STUDY_DATE,
                        QUERY_STARTED_AT,
                        QUERY_ENDED_AT
                );

        assertThat(rows)
                .extracting(StudyBreakIntervalRow::sessionId)
                .containsExactly(matching.getId());
        assertThat(rows.getFirst().branchId()).isEqualTo(2L);
        assertThat(rows.getFirst().memberId()).isEqualTo(1L);
    }

    private StudyBreakSession session(Long presenceSessionId, Long memberId, Instant startedAt) {
        return session(presenceSessionId, memberId, 2L, STUDY_DATE, startedAt);
    }

    private StudyBreakSession session(
            Long presenceSessionId,
            Long memberId,
            Long branchId,
            LocalDate studyDate,
            Instant startedAt
    ) {
        return new StudyBreakSession(
                presenceSessionId,
                memberId,
                branchId,
                studyDate,
                StudyBreak.AFTER_FIRST,
                BREAK_STARTED_AT,
                BREAK_ENDED_AT,
                startedAt
        );
    }

    private StudyBreakSession closedSession(
            Long presenceSessionId,
            Long memberId,
            Instant startedAt,
            Instant endedAt
    ) {
        return closedSession(
                presenceSessionId,
                memberId,
                2L,
                STUDY_DATE,
                startedAt,
                endedAt
        );
    }

    private StudyBreakSession closedSession(
            Long presenceSessionId,
            Long memberId,
            Long branchId,
            LocalDate studyDate,
            Instant startedAt,
            Instant endedAt
    ) {
        StudyBreakSession session = session(
                presenceSessionId,
                memberId,
                branchId,
                studyDate,
                startedAt
        );
        session.stopByMember(endedAt);
        return session;
    }
}
