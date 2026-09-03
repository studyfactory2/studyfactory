package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceCheckoutMethod;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습실 입퇴실 운영 조회 서비스 테스트")
class StudyPresenceQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T06:00:00Z");
    private static final Instant AFTER_SEOUL_MIDNIGHT = Instant.parse("2026-08-28T15:00:05Z");

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    private StudyPresenceQueryService studyPresenceQueryService;

    @BeforeEach
    void setUp() {
        studyPresenceQueryService = new StudyPresenceQueryService(
                studyPresenceSessionRepository,
                memberRepository,
                new StudyPresenceAutoClosePolicy(true),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("스태프는 현재 지점의 입실자와 시분초 체류 시간을 조회한다")
    void findLiveForCurrentBranch() {
        Member manager = createMember(9L, 2L, "이스태프", MemberRole.STAFF, null);
        Member target = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession session = activeSession(10L, 1L, 2L, NOW.minusSeconds(27_738));
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findActiveByBranchId(2L)).willReturn(List.of(session));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(target));

        var response = studyPresenceQueryService.findLive(9L);

        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.asOf()).isEqualTo(NOW);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.memberName()).isEqualTo("김회원");
            assertThat(item.seatNumber()).isEqualTo(10);
            assertThat(item.presenceDuration().totalSeconds()).isEqualTo(27_738);
            assertThat(item.presenceDuration().hours()).isEqualTo(7);
            assertThat(item.presenceDuration().minutes()).isEqualTo(42);
            assertThat(item.presenceDuration().seconds()).isEqualTo(18);
            assertThat(item.presenceDuration().formatted()).isEqualTo("07:42:18");
        });
        then(studyPresenceSessionRepository).should().findActiveByBranchId(2L);
    }

    @Test
    @DisplayName("스케줄러 저장 직전의 어제 미퇴실 기록은 실시간 입실자에서 숨긴다")
    void hidePendingAutomaticCheckoutFromLivePresence() {
        Member manager = createMember(9L, 2L, "이스태프", MemberRole.STAFF, null);
        StudyPresenceSession staleSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findActiveByBranchId(2L)).willReturn(List.of(staleSession));
        StudyPresenceQueryService queryService = queryServiceAt(AFTER_SEOUL_MIDNIGHT);

        var response = queryService.findLive(9L);

        assertThat(response.memberCount()).isZero();
        assertThat(response.sessions()).isEmpty();
        then(memberRepository).should().findAllById(List.of());
    }

    @Test
    @DisplayName("일반 회원은 운영용 실시간 입실 현황을 조회할 수 없다")
    void rejectLiveForMember() {
        Member member = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> studyPresenceQueryService.findLive(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일별 이력은 서울 날짜 경계로 체류 구간을 잘라서 계산한다")
    void findDailyHistoryWithSeoulWindowClipping() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        Member target = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession session = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-27T14:00:00Z")
        );
        session.checkOut(Instant.parse("2026-08-27T16:00:00Z"));
        Instant seoulDayStart = Instant.parse("2026-08-27T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchId(2L, seoulDayStart, NOW))
                .willReturn(List.of(session));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(target));

        var response = studyPresenceQueryService.findDailyHistory(9L, LocalDate.of(2026, 8, 28));

        assertThat(response.fromDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(response.toDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(response.sessionCount()).isEqualTo(1);
        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.checkedInAt()).isEqualTo(Instant.parse("2026-08-27T14:00:00Z"));
            assertThat(item.overlapStartedAt()).isEqualTo(seoulDayStart);
            assertThat(item.overlapEndedAt()).isEqualTo(Instant.parse("2026-08-27T16:00:00Z"));
            assertThat(item.presenceDuration().totalSeconds()).isEqualTo(3_600);
            assertThat(item.presenceDuration().formatted()).isEqualTo("01:00:00");
        });
    }

    @Test
    @DisplayName("날짜를 생략하면 현재 서울 날짜의 이력을 조회한다")
    void defaultDailyHistoryDateToCurrentSeoulDate() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        Instant seoulDayStart = Instant.parse("2026-08-27T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchId(2L, seoulDayStart, NOW))
                .willReturn(List.of());
        given(memberRepository.findAllById(List.of())).willReturn(List.of());

        var response = studyPresenceQueryService.findDailyHistory(9L, null);

        assertThat(response.fromDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(response.sessions()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 회원의 일별 입퇴실 기록도 회원 식별자를 보존해 반환한다")
    void retainDeletedMemberHistory() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        StudyPresenceSession session = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T00:00:00Z")
        );
        session.closeForMemberDeletion(Instant.parse("2026-08-28T01:00:00Z"));
        Instant seoulDayStart = Instant.parse("2026-08-27T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchId(2L, seoulDayStart, NOW))
                .willReturn(List.of(session));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of());

        var response = studyPresenceQueryService.findDailyHistory(9L, LocalDate.of(2026, 8, 28));

        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.memberId()).isEqualTo(1L);
            assertThat(item.memberName()).isNull();
            assertThat(item.memberRole()).isNull();
            assertThat(item.seatNumber()).isNull();
            assertThat(item.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.MEMBER_DELETED);
        });
    }

    @Test
    @DisplayName("스케줄러 저장 직전에도 어제 이력은 자정 자동 퇴실로 일관되게 계산한다")
    void representPendingAutomaticCheckoutInHistory() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        Member target = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession staleSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        Instant previousDayStart = Instant.parse("2026-08-27T15:00:00Z");
        Instant previousDayEnd = Instant.parse("2026-08-28T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchId(
                2L,
                previousDayStart,
                previousDayEnd
        )).willReturn(List.of(staleSession));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(target));
        StudyPresenceQueryService queryService = queryServiceAt(AFTER_SEOUL_MIDNIGHT);

        var response = queryService.findDailyHistory(9L, LocalDate.of(2026, 8, 28));

        assertThat(response.totalPresenceDuration().formatted()).isEqualTo("01:00:00");
        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.checkedOutAt()).isEqualTo(previousDayEnd);
            assertThat(item.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.AUTO_MIDNIGHT);
            assertThat(item.currentlyActive()).isFalse();
            assertThat(item.presenceDuration().formatted()).isEqualTo("01:00:00");
        });
    }

    @Test
    @DisplayName("스케줄러 저장 직전의 어제 기록은 새 날짜 일별 이력에 0초 행으로 남기지 않는다")
    void excludePendingAutomaticCheckoutFromNewDayHistory() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        StudyPresenceSession staleSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        Instant currentDayStart = Instant.parse("2026-08-28T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchId(
                2L,
                currentDayStart,
                AFTER_SEOUL_MIDNIGHT
        )).willReturn(List.of(staleSession));
        StudyPresenceQueryService queryService = queryServiceAt(AFTER_SEOUL_MIDNIGHT);

        var response = queryService.findDailyHistory(9L, LocalDate.of(2026, 8, 29));

        assertThat(response.sessionCount()).isZero();
        assertThat(response.totalPresenceDuration().totalSeconds()).isZero();
        assertThat(response.sessions()).isEmpty();
        then(memberRepository).should().findAllById(List.of());
    }

    @Test
    @DisplayName("스케줄러 저장 직전의 어제 기록은 새 날짜 회원 이력에도 남기지 않는다")
    void excludePendingAutomaticCheckoutFromNewDayMemberHistory() {
        Member manager = createMember(9L, 2L, "이스태프", MemberRole.STAFF, null);
        StudyPresenceSession staleSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        Instant currentDayStart = Instant.parse("2026-08-28T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                2L,
                1L,
                currentDayStart,
                AFTER_SEOUL_MIDNIGHT
        )).willReturn(List.of(staleSession));
        StudyPresenceQueryService queryService = queryServiceAt(AFTER_SEOUL_MIDNIGHT);

        var response = queryService.findMemberHistory(
                9L,
                1L,
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 29)
        );

        assertThat(response.sessionCount()).isZero();
        assertThat(response.totalPresenceDuration().totalSeconds()).isZero();
        assertThat(response.sessions()).isEmpty();
        then(memberRepository).should().findAllById(List.of());
    }

    @Test
    @DisplayName("같은 지점 회원의 기간별 이력만 지점과 회원 조건으로 조회한다")
    void findMemberHistoryForCurrentBranch() {
        Member manager = createMember(9L, 2L, "이스태프", MemberRole.STAFF, null);
        Member target = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession session = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T05:00:00Z")
        );
        Instant rangeStart = Instant.parse("2026-08-26T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                2L,
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of(session));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(target));

        var response = studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 28)
        );

        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.branchId()).isEqualTo(2L);
            assertThat(item.currentlyActive()).isTrue();
            assertThat(item.presenceDuration().totalSeconds()).isEqualTo(3_600);
        });
    }

    @Test
    @DisplayName("회원은 지점 이동 전후의 자기 입퇴실 시각과 범위 체류시간을 함께 조회한다")
    void findOwnHistoryAcrossBranches() {
        Member member = createMember(1L, 3L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession oldBranchSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-27T14:00:00Z")
        );
        oldBranchSession.checkOut(Instant.parse("2026-08-27T16:00:00Z"));
        StudyPresenceSession currentBranchSession = activeSession(
                11L,
                1L,
                3L,
                Instant.parse("2026-08-28T05:00:00Z")
        );
        Instant rangeStart = Instant.parse("2026-08-27T15:00:00Z");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findOverlappingByMemberId(
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of(oldBranchSession, currentBranchSession));

        var response = studyPresenceQueryService.findMyHistory(
                1L,
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 28)
        );

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(response.asOf()).isEqualTo(NOW);
        assertThat(response.sessionCount()).isEqualTo(2);
        assertThat(response.totalPresenceDuration().formatted()).isEqualTo("02:00:00");
        assertThat(response.sessions()).satisfiesExactly(
                oldSession -> {
                    assertThat(oldSession.branchId()).isEqualTo(2L);
                    assertThat(oldSession.checkedInAt()).isEqualTo(Instant.parse("2026-08-27T14:00:00Z"));
                    assertThat(oldSession.checkedOutAt()).isEqualTo(Instant.parse("2026-08-27T16:00:00Z"));
                    assertThat(oldSession.overlapStartedAt()).isEqualTo(rangeStart);
                    assertThat(oldSession.presenceDuration().formatted()).isEqualTo("01:00:00");
                    assertThat(oldSession.currentlyActive()).isFalse();
                },
                currentSession -> {
                    assertThat(currentSession.branchId()).isEqualTo(3L);
                    assertThat(currentSession.checkedOutAt()).isNull();
                    assertThat(currentSession.overlapEndedAt()).isEqualTo(NOW);
                    assertThat(currentSession.presenceDuration().formatted()).isEqualTo("01:00:00");
                    assertThat(currentSession.currentlyActive()).isTrue();
                }
        );
        then(studyPresenceSessionRepository).should().findOverlappingByMemberId(
                1L,
                rangeStart,
                NOW
        );
    }

    @Test
    @DisplayName("회원 자기 이력도 스케줄러 저장 전의 오래된 활성 기록을 서울 자정에 종료해 보여준다")
    void representPendingAutomaticCheckoutInOwnHistory() {
        Member member = createMember(1L, 2L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession staleSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T14:00:00Z")
        );
        Instant windowStart = Instant.parse("2026-08-27T15:00:00Z");
        Instant automaticCheckoutAt = Instant.parse("2026-08-28T15:00:00Z");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findOverlappingByMemberId(
                1L,
                windowStart,
                automaticCheckoutAt
        )).willReturn(List.of(staleSession));
        StudyPresenceQueryService queryService = queryServiceAt(AFTER_SEOUL_MIDNIGHT);

        var response = queryService.findMyHistory(
                1L,
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 28)
        );

        assertThat(response.sessions()).singleElement().satisfies(session -> {
            assertThat(session.checkedOutAt()).isEqualTo(automaticCheckoutAt);
            assertThat(session.currentlyActive()).isFalse();
            assertThat(session.presenceDuration().formatted()).isEqualTo("01:00:00");
        });
    }

    @Test
    @DisplayName("회원이 지점을 옮겨도 이전 지점은 그 지점에서 생성된 이력만 조회한다")
    void findTransferredMemberHistoryWithinManagerBranch() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        Member transferredMember = createMember(1L, 3L, "김회원", MemberRole.MEMBER, 10);
        StudyPresenceSession oldBranchSession = activeSession(
                10L,
                1L,
                2L,
                Instant.parse("2026-08-28T05:00:00Z")
        );
        Instant rangeStart = Instant.parse("2026-08-27T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                2L,
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of(oldBranchSession));
        given(memberRepository.findAllById(List.of(1L))).willReturn(List.of(transferredMember));

        var response = studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 28)
        );

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.sessions()).singleElement().satisfies(item -> {
            assertThat(item.branchId()).isEqualTo(2L);
            assertThat(item.memberName()).isNull();
            assertThat(item.memberRole()).isNull();
            assertThat(item.seatNumber()).isNull();
        });
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦거나 조회 범위가 1년을 넘으면 거절한다")
    void rejectInvalidMemberHistoryRange() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));

        assertThatThrownBy(() -> studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 27)
        )).isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("조회 시작일은 종료일보다 늦을 수 없습니다.");

        assertThatThrownBy(() -> studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2026, 8, 3)
        )).isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("최대 1년");
    }

    @Test
    @DisplayName("기간 이력은 최대 366개 날짜를 허용하고 그보다 하루 길면 거절한다")
    void enforceInclusiveHistoryRangeBoundary() {
        Member manager = createMember(9L, 2L, "김관리자", MemberRole.ADMIN, null);
        LocalDate fromDate = LocalDate.of(2025, 8, 28);
        LocalDate acceptedToDate = fromDate.plusDays(365);
        Instant rangeStart = Instant.parse("2025-08-27T15:00:00Z");
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findOverlappingByBranchIdAndMemberId(
                2L,
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(memberRepository.findAllById(List.of())).willReturn(List.of());

        var accepted = studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                fromDate,
                acceptedToDate
        );

        assertThat(accepted.fromDate()).isEqualTo(fromDate);
        assertThat(accepted.toDate()).isEqualTo(acceptedToDate);
        assertThat(accepted.sessionCount()).isZero();

        assertThatThrownBy(() -> studyPresenceQueryService.findMemberHistory(
                9L,
                1L,
                fromDate,
                fromDate.plusDays(366)
        )).isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("최대 1년");
    }

    private StudyPresenceSession activeSession(
            Long sessionId,
            Long memberId,
            Long branchId,
            Instant checkedInAt
    ) {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(memberId, branchId, checkedInAt);
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    private Member createMember(
            Long id,
            Long branchId,
            String name,
            MemberRole role,
            Integer seatNumber
    ) {
        Member member = new Member(
                branchId,
                name,
                "password",
                role,
                seatNumber,
                LocalDate.of(2026, 8, 1),
                3L
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private StudyPresenceQueryService queryServiceAt(Instant instant) {
        return new StudyPresenceQueryService(
                studyPresenceSessionRepository,
                memberRepository,
                new StudyPresenceAutoClosePolicy(true),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
