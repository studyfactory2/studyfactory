package com.example.studyfactory.domain.studyTime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.model.StudyPresenceIntervalRow;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import com.example.studyfactory.domain.studyTime.exception.StudyTimeException;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("인정 학습시간 리포트 서비스 테스트")
class StudyTimeReportServiceTest {

    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);
    private static final Instant NOW = atSeoul(STUDY_DATE, 22, 30);

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private StudyBreakSessionRepository studyBreakSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudyTimeLeaveExclusionService leaveExclusionService;

    private StudyTimeReportService studyTimeReportService;

    @BeforeEach
    void setUp() {
        studyTimeReportService = serviceAt(NOW, true);
    }

    @Test
    @DisplayName("입실, 시간표, 유효 휴무, 명시적 휴식 공부를 합쳐 일별 및 전체 시간을 반환한다")
    void calculateRecognizedStudyTimeReport() {
        Member member = member(1L, 2L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findIntervalRowsByMemberId(1L, rangeStart, NOW))
                .willReturn(List.of(presence(101L, 1L, 2L, 9, 0, 22, 0)));
        given(studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        )).willReturn(List.of(breakSession(
                201L,
                101L,
                1L,
                2L,
                StudyBreak.AFTER_FIRST,
                10,
                30,
                10,
                45
        )));
        given(leaveExclusionService.findExcludedPeriods(1L, STUDY_DATE, STUDY_DATE))
                .willReturn(Map.of(STUDY_DATE, Set.of(StudyPeriod.FOURTH)));

        var response = studyTimeReportService.findMine(1L, STUDY_DATE, STUDY_DATE);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(response.asOf()).isEqualTo(NOW);
        assertThat(response.attendedDayCount()).isEqualTo(1);
        assertThat(response.totals().presenceDuration().formatted()).isEqualTo("13:00:00");
        assertThat(response.totals().recognizedPeriodDuration().formatted()).isEqualTo("08:00:00");
        assertThat(response.totals().recognizedBreakDuration().formatted()).isEqualTo("00:15:00");
        assertThat(response.totals().totalRecognizedStudyDuration().formatted()).isEqualTo("08:15:00");
        assertThat(response.days()).singleElement().satisfies(day -> {
            assertThat(day.studyDate()).isEqualTo(STUDY_DATE);
            assertThat(day.excludedPeriods()).containsExactly(StudyPeriod.FOURTH);
            assertThat(day.periods()).hasSize(7);
            assertThat(day.breaks()).hasSize(6);
            assertThat(day.periods())
                    .filteredOn(period -> period.period() == StudyPeriod.FOURTH)
                    .singleElement()
                    .satisfies(period -> {
                        assertThat(period.weeklyPlanIndex()).isEqualTo(3);
                        assertThat(period.excludedByLeave()).isTrue();
                        assertThat(period.recognizedDuration().totalSeconds()).isZero();
                    });
            assertThat(day.breaks())
                    .filteredOn(studyBreak -> studyBreak.studyBreak() == StudyBreak.AFTER_FIRST)
                    .singleElement()
                    .satisfies(studyBreak ->
                            assertThat(studyBreak.recognizedDuration().formatted()).isEqualTo("00:15:00"));
        });
    }

    @Test
    @DisplayName("겹치는 입실 기록은 합쳐 실제 합집합 시간만 계산한다")
    void mergeOverlappingPresenceIntervals() {
        Member member = member(1L, 2L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findIntervalRowsByMemberId(1L, rangeStart, NOW))
                .willReturn(List.of(
                        presence(101L, 1L, 2L, 9, 0, 10, 0),
                        presence(102L, 1L, 2L, 9, 30, 10, 30)
                ));
        given(studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(leaveExclusionService.findExcludedPeriods(1L, STUDY_DATE, STUDY_DATE))
                .willReturn(Map.of());

        var response = studyTimeReportService.findMine(1L, STUDY_DATE, STUDY_DATE);

        assertThat(response.totals().presenceDuration().formatted()).isEqualTo("01:30:00");
        assertThat(response.totals().recognizedPeriodDuration().formatted()).isEqualTo("01:30:00");
    }

    @Test
    @DisplayName("휴식 공부는 연결된 입실 기록의 실제 퇴실 시각까지만 인정한다")
    void capBreakStudyAtItsParentPresenceEnd() {
        Member member = member(1L, 2L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        StudyPresenceIntervalRow presence = new StudyPresenceIntervalRow(
                101L,
                1L,
                2L,
                atSeoul(STUDY_DATE, 9, 0),
                atSeoul(STUDY_DATE, 10, 37)
        );
        StudyBreakIntervalRow openBreak = new StudyBreakIntervalRow(
                201L,
                101L,
                1L,
                2L,
                STUDY_DATE,
                StudyBreak.AFTER_FIRST,
                atSeoul(STUDY_DATE, 10, 35),
                null,
                atSeoul(STUDY_DATE, 10, 45)
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findIntervalRowsByMemberId(1L, rangeStart, NOW))
                .willReturn(List.of(presence));
        given(studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        )).willReturn(List.of(openBreak));
        given(leaveExclusionService.findExcludedPeriods(1L, STUDY_DATE, STUDY_DATE))
                .willReturn(Map.of());

        var response = studyTimeReportService.findMine(1L, STUDY_DATE, STUDY_DATE);

        assertThat(response.totals().recognizedBreakDuration().formatted()).isEqualTo("00:02:00");
    }

    @Test
    @DisplayName("활성 입실이 서울 자정을 넘기면 저장 전에도 첫 자정에서 논리적으로 종료한다")
    void splitAndVirtuallyCloseStalePresenceAtSeoulMidnight() {
        LocalDate nextDate = STUDY_DATE.plusDays(1);
        Instant justAfterMidnight = atSeoul(nextDate, 0, 0).plusSeconds(5);
        StudyTimeReportService service = serviceAt(justAfterMidnight, true);
        Member member = member(1L, 2L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findIntervalRowsByMemberId(1L, rangeStart, justAfterMidnight))
                .willReturn(List.of(new StudyPresenceIntervalRow(
                        101L,
                        1L,
                        2L,
                        atSeoul(STUDY_DATE, 23, 0),
                        null
                )));
        given(studyBreakSessionRepository.findIntervalRowsByMemberId(
                1L,
                STUDY_DATE,
                nextDate,
                rangeStart,
                justAfterMidnight
        )).willReturn(List.of());
        given(leaveExclusionService.findExcludedPeriods(1L, STUDY_DATE, nextDate))
                .willReturn(Map.of());

        var response = service.findMine(1L, STUDY_DATE, nextDate);

        assertThat(response.days()).extracting(day -> day.presenceDuration().totalSeconds())
                .containsExactly(3_600L, 0L);
        assertThat(response.attendedDayCount()).isEqualTo(1);
        assertThat(response.totals().presenceDuration().formatted()).isEqualTo("01:00:00");
    }

    @Test
    @DisplayName("스태프는 같은 지점 회원을 지점 범위 쿼리로 조회한다")
    void scopeManagerReportToCurrentBranch() {
        Member manager = member(9L, 2L, MemberRole.STAFF);
        Member target = member(1L, 2L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(memberRepository.findByIdAndReferenceInformationBranchId(1L, 2L))
                .willReturn(Optional.of(target));
        given(studyPresenceSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                2L,
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(studyBreakSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                2L,
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(leaveExclusionService.findExcludedPeriods(1L, 2L, STUDY_DATE, STUDY_DATE))
                .willReturn(Map.of());

        var response = studyTimeReportService.findForManager(9L, 1L, STUDY_DATE, STUDY_DATE);

        assertThat(response.memberId()).isEqualTo(1L);
        then(studyPresenceSessionRepository).should().findIntervalRowsByBranchIdAndMemberId(
                2L,
                1L,
                rangeStart,
                NOW
        );
        then(studyBreakSessionRepository).should().findIntervalRowsByBranchIdAndMemberId(
                2L,
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        );
        then(leaveExclusionService).should().findExcludedPeriods(
                1L,
                2L,
                STUDY_DATE,
                STUDY_DATE
        );
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
        then(studyBreakSessionRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일반 회원의 운영 조회와 스태프의 다른 지점 회원 조회는 거절한다")
    void rejectUnauthorizedManagerReports() {
        Member member = member(1L, 2L, MemberRole.MEMBER);
        Member manager = member(9L, 2L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> studyTimeReportService.findForManager(
                1L,
                3L,
                STUDY_DATE,
                STUDY_DATE
        )).isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(memberRepository.findByIdAndReferenceInformationBranchId(3L, 2L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyTimeReportService.findForManager(
                9L,
                3L,
                STUDY_DATE,
                STUDY_DATE
        )).isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");

        then(memberRepository).should().findByIdAndReferenceInformationBranchId(3L, 2L);
        then(memberRepository).should(never()).findById(3L);
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
        then(studyBreakSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자는 다른 지점 회원 리포트를 대상 회원의 지점 범위로 조회한다")
    void adminFindsAnotherBranchMemberReport() {
        Member admin = member(9L, 2L, MemberRole.ADMIN);
        Member target = member(1L, 3L, MemberRole.MEMBER);
        Instant rangeStart = atSeoul(STUDY_DATE, 0, 0);
        given(memberRepository.findById(9L)).willReturn(Optional.of(admin));
        given(memberRepository.findById(1L)).willReturn(Optional.of(target));
        given(studyPresenceSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                3L,
                1L,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(studyBreakSessionRepository.findIntervalRowsByBranchIdAndMemberId(
                3L,
                1L,
                STUDY_DATE,
                STUDY_DATE,
                rangeStart,
                NOW
        )).willReturn(List.of());
        given(leaveExclusionService.findExcludedPeriods(1L, 3L, STUDY_DATE, STUDY_DATE))
                .willReturn(Map.of());

        var response = studyTimeReportService.findForManager(9L, 1L, STUDY_DATE, STUDY_DATE);

        assertThat(response.branchId()).isEqualTo(3L);
        then(studyPresenceSessionRepository).should().findIntervalRowsByBranchIdAndMemberId(
                3L,
                1L,
                rangeStart,
                NOW
        );
    }

    @Test
    @DisplayName("최대 366일을 허용하고 역순 또는 367일 범위를 거절한다")
    void validateReportDateRange() {
        LocalDate fromDate = LocalDate.of(2025, 8, 29);
        LocalDate acceptedToDate = fromDate.plusDays(365);
        Member member = member(1L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(leaveExclusionService.findExcludedPeriods(1L, fromDate, acceptedToDate))
                .willReturn(Map.of());

        var accepted = studyTimeReportService.findMine(1L, fromDate, acceptedToDate);

        assertThat(accepted.days()).hasSize(366);

        assertThatThrownBy(() -> studyTimeReportService.findMine(
                1L,
                STUDY_DATE,
                STUDY_DATE.minusDays(1)
        )).isInstanceOf(StudyTimeException.class)
                .hasMessageContaining("조회 시작일");
        assertThatThrownBy(() -> studyTimeReportService.findMine(
                1L,
                fromDate,
                fromDate.plusDays(366)
        )).isInstanceOf(StudyTimeException.class)
                .hasMessageContaining("최대 1년");
    }

    private StudyTimeReportService serviceAt(Instant now, boolean autoCloseEnabled) {
        return new StudyTimeReportService(
                studyPresenceSessionRepository,
                studyBreakSessionRepository,
                memberRepository,
                new StudyTimeCalculator(),
                leaveExclusionService,
                new StudyPresenceAutoClosePolicy(autoCloseEnabled),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private Member member(Long id, Long branchId, MemberRole role) {
        Member member = new Member(
                branchId,
                "회원" + id,
                "password",
                role,
                10,
                LocalDate.of(2026, 8, 1),
                3L
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private StudyPresenceIntervalRow presence(
            Long sessionId,
            Long memberId,
            Long branchId,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return new StudyPresenceIntervalRow(
                sessionId,
                memberId,
                branchId,
                atSeoul(STUDY_DATE, startHour, startMinute),
                atSeoul(STUDY_DATE, endHour, endMinute)
        );
    }

    private StudyBreakIntervalRow breakSession(
            Long sessionId,
            Long presenceSessionId,
            Long memberId,
            Long branchId,
            StudyBreak studyBreak,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return new StudyBreakIntervalRow(
                sessionId,
                presenceSessionId,
                memberId,
                branchId,
                STUDY_DATE,
                studyBreak,
                atSeoul(STUDY_DATE, startHour, startMinute),
                atSeoul(STUDY_DATE, endHour, endMinute),
                STUDY_DATE.atTime(studyBreak.getEndTime()).atZone(StudyTimeCalculator.STUDY_ZONE).toInstant()
        );
    }

    private static Instant atSeoul(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
                .atZone(StudyTimeCalculator.STUDY_ZONE)
                .toInstant();
    }
}
