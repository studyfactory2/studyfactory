package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.service.StudyBreakLifecycleService;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceManagerSessionResponse;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCheckInMethod;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyPresence.qr.StudyPresenceQrTokenProvider;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습실 관리자 수동 입실 서비스 테스트")
class StudyPresenceManualCheckInServiceTest {

    /** 2026-09-02 10:00 Asia/Seoul. */
    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    /** 08:45 Asia/Seoul on the same day — before the first period opens. */
    private static final Instant BEFORE_FIRST_PERIOD = Instant.parse("2026-09-01T23:45:00Z");
    /** 23:00 Asia/Seoul on the previous day. */
    private static final Instant PREVIOUS_SEOUL_DAY = Instant.parse("2026-09-01T14:00:00Z");
    /** 2026-09-01 23:59 Asia/Seoul — one minute before the day rolls over. */
    private static final Instant BEFORE_SEOUL_MIDNIGHT = Instant.parse("2026-09-01T14:59:00Z");
    /** 2026-09-02 00:00:30 Asia/Seoul — the same wall clock, thirty seconds later. */
    private static final Instant AFTER_SEOUL_MIDNIGHT = Instant.parse("2026-09-01T15:00:30Z");
    /** 2026-09-01 23:30 Asia/Seoul — today before midnight, yesterday after it. */
    private static final Instant LATE_ON_PREVIOUS_DAY = Instant.parse("2026-09-01T14:30:00Z");
    private static final String REASON = "출입문 QR 인식 오류";

    @InjectMocks
    private StudyPresenceService studyPresenceService;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

    @Spy
    private StudyPresenceAutoClosePolicy autoClosePolicy = new StudyPresenceAutoClosePolicy(true);

    @Mock
    private StudyBreakLifecycleService studyBreakLifecycleService;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("관리자는 같은 지점 회원의 입실을 사유와 함께 수동으로 기록한다")
    void adminManuallyChecksInSameBranchMember() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenManualCheckInIsPossible(admin, target, BEFORE_FIRST_PERIOD);

        StudyPresenceManagerSessionResponse response = studyPresenceService.managerCheckIn(
                9L,
                1L,
                BEFORE_FIRST_PERIOD,
                "  " + REASON + "  "
        );

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.checkedInAt()).isEqualTo(BEFORE_FIRST_PERIOD);
        assertThat(response.checkInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(response.checkedInByMemberId()).isEqualTo(9L);
        assertThat(response.manualCheckInReason()).isEqualTo(REASON);
        assertThat(response.currentlyActive()).isTrue();
        assertThat(response.checkedOutAt()).isNull();
        then(studyPresenceQrTokenProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("스태프도 같은 지점 회원의 입실을 수동으로 기록한다")
    void staffManuallyChecksInSameBranchMember() {
        Member staff = createMember(8L, 2L, MemberRole.STAFF);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenManualCheckInIsPossible(staff, target, BEFORE_FIRST_PERIOD);

        StudyPresenceManagerSessionResponse response = studyPresenceService.managerCheckIn(
                8L,
                1L,
                BEFORE_FIRST_PERIOD,
                REASON
        );

        assertThat(response.checkInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(response.checkedInByMemberId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("일반 회원은 다른 회원을 수동으로 입실 처리할 수 없다")
    void rejectManualCheckInForMemberOperator() {
        Member operator = createMember(7L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(7L)).willReturn(Optional.of(operator));

        assertThatThrownBy(() -> studyPresenceService.managerCheckIn(7L, 1L, BEFORE_FIRST_PERIOD, REASON))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("스태프는 다른 지점 회원을 잠그거나 조회하지 않고 없는 회원과 같이 처리한다")
    void rejectManualCheckInForOtherBranchTarget() {
        Member staff = createMember(9L, 2L, MemberRole.STAFF);
        given(memberRepository.findById(9L)).willReturn(Optional.of(staff));
        given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(1L, 2L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, REASON))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");
        then(memberRepository).should(never()).findByIdForUpdate(1L);
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관리자는 다른 지점 회원도 선택한 지점 범위에서 수동 입실 처리한다")
    void adminManuallyChecksInOtherBranchMember() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 3L, MemberRole.MEMBER);
        givenManualCheckInIsPossible(admin, target, BEFORE_FIRST_PERIOD);

        StudyPresenceManagerSessionResponse response = studyPresenceService.managerCheckIn(
                9L,
                1L,
                BEFORE_FIRST_PERIOD,
                REASON
        );

        assertThat(response.branchId()).isEqualTo(3L);
        assertThat(response.checkedInByMemberId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("회원이 아닌 대상은 수동 입실 처리할 수 없다")
    void rejectManualCheckInForNonMemberTarget() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.STAFF);
        given(memberRepository.findById(9L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(target));

        assertThatThrownBy(() -> studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, REASON))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("미래 시각으로는 수동 입실 처리할 수 없다")
    void rejectFutureManualCheckInTime() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenLocksAreHeld(admin, target);

        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, NOW.plusSeconds(60), REASON))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("수동 입실 시각은 미래일 수 없습니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("어제 서울 날짜의 시각으로는 수동 입실 처리할 수 없다")
    void rejectPreviousSeoulDayManualCheckInTime() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenLocksAreHeld(admin, target);

        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, PREVIOUS_SEOUL_DAY, REASON))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("수동 입실은 오늘 날짜의 시각만 등록할 수 있습니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("잠금 대기가 서울 자정을 넘기면 잠금 이후에 읽은 시각으로 오늘 여부를 판정한다")
    void usesTheClockReadAfterTheLockWhenTheWaitCrossesSeoulMidnight() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        AtomicReference<Instant> serverNow = new AtomicReference<>(BEFORE_SEOUL_MIDNIGHT);
        given(memberRepository.findById(9L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(target));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willAnswer(invocation -> {
                    // the pessimistic lock wait spans the Seoul day boundary
                    serverNow.set(AFTER_SEOUL_MIDNIGHT);
                    return Optional.empty();
                });
        given(clock.instant()).willAnswer(invocation -> serverNow.get());

        // 23:30 was "today" when the request arrived and is "yesterday" once the
        // lock is finally held; the post-lock reading is the authoritative one
        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, LATE_ON_PREVIOUS_DAY, REASON))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("수동 입실은 오늘 날짜의 시각만 등록할 수 있습니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("생략하거나 공백인 수동 입실 사유는 빈 감사 메모로 정규화한다")
    void normalizeBlankManualCheckInReasonToNull() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenManualCheckInIsPossible(admin, target, BEFORE_FIRST_PERIOD);

        StudyPresenceManagerSessionResponse response = studyPresenceService.managerCheckIn(
                9L,
                1L,
                BEFORE_FIRST_PERIOD,
                "   "
        );

        assertThat(response.checkInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(response.checkedInByMemberId()).isEqualTo(9L);
        assertThat(response.manualCheckInReason()).isNull();
    }

    @Test
    @DisplayName("200자를 넘는 사유로는 수동 입실 처리할 수 없다")
    void rejectOversizedManualCheckInReason() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenManualCheckInReachesCreation(admin, target, BEFORE_FIRST_PERIOD);

        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, "가".repeat(201)))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("수동 입실 사유는 200자를 넘을 수 없습니다.");
    }

    @Test
    @DisplayName("이미 입실 중인 회원은 수동으로 다시 입실 처리할 수 없다")
    void rejectManualCheckInForAlreadyActiveSession() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        StudyPresenceSession activeSession =
                StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(1_800));
        given(memberRepository.findById(9L)).willReturn(Optional.of(admin));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(target));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, REASON))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("이미 입실 처리된 회원입니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("요청 구간이 기존 기록과 겹치면 수동 입실을 거절한다")
    void rejectManualCheckInOverlappingExistingSession() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        StudyPresenceSession earlierSession =
                StudyPresenceSession.qrCheckIn(1L, 2L, BEFORE_FIRST_PERIOD.plusSeconds(600));
        earlierSession.checkOut(NOW.minusSeconds(600));
        givenLocksAreHeld(admin, target);
        given(studyPresenceSessionRepository.findOverlappingByMemberId(1L, BEFORE_FIRST_PERIOD, NOW))
                .willReturn(List.of(earlierSession));

        assertThatThrownBy(() ->
                studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, REASON))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("해당 시간대에 이미 입퇴실 기록이 있습니다.");
        then(studyPresenceSessionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("QR 입실은 본인 명의로, 수동 입실은 담당자 명의로 감사 정보를 남긴다")
    void storesCheckInAuditFieldsForBothMethods() {
        StudyPresenceSession qrSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW);

        assertThat(qrSession.getCheckInMethod()).isEqualTo(StudyPresenceCheckInMethod.QR);
        assertThat(qrSession.getCheckedInByMemberId()).isEqualTo(1L);
        assertThat(qrSession.getManualCheckInReason()).isNull();
        assertThat(qrSession.isManuallyCheckedIn()).isFalse();

        StudyPresenceSession managerSession =
                StudyPresenceSession.managerCheckIn(1L, 2L, NOW, 9L, "  " + REASON + " ");

        assertThat(managerSession.getCheckInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(managerSession.getCheckedInByMemberId()).isEqualTo(9L);
        assertThat(managerSession.getManualCheckInReason()).isEqualTo(REASON);
        assertThat(managerSession.isManuallyCheckedIn()).isTrue();
        assertThat(managerSession.isActive()).isTrue();

        StudyPresenceSession managerSessionWithoutReason =
                StudyPresenceSession.managerCheckIn(2L, 2L, NOW, 9L, null);

        assertThat(managerSessionWithoutReason.getCheckInMethod()).isEqualTo(StudyPresenceCheckInMethod.MANAGER);
        assertThat(managerSessionWithoutReason.getCheckedInByMemberId()).isEqualTo(9L);
        assertThat(managerSessionWithoutReason.getManualCheckInReason()).isNull();
    }

    @Test
    @DisplayName("수동 입실도 QR 입실과 동일하게 회원 행과 활성 기록을 잠그고 진행한다")
    void locksMemberAndActiveSessionLikeQrCheckIn() {
        Member admin = createMember(9L, 2L, MemberRole.ADMIN);
        Member target = createMember(1L, 2L, MemberRole.MEMBER);
        givenManualCheckInIsPossible(admin, target, BEFORE_FIRST_PERIOD);

        studyPresenceService.managerCheckIn(9L, 1L, BEFORE_FIRST_PERIOD, REASON);

        then(memberRepository).should().findByIdForUpdate(1L);
        then(studyPresenceSessionRepository).should().findActiveByMemberIdForUpdate(1L);
        then(studyPresenceSessionRepository).should().save(any(StudyPresenceSession.class));
    }

    /** Operator resolved, both pessimistic locks taken, clock ready to be read. */
    private void givenLocksAreHeld(Member operator, Member target) {
        given(memberRepository.findById(operator.getId())).willReturn(Optional.of(operator));
        if (operator.getRole() == MemberRole.ADMIN) {
            given(memberRepository.findByIdForUpdate(target.getId())).willReturn(Optional.of(target));
        } else {
            given(memberRepository.findByIdAndReferenceInformationBranchIdForUpdate(
                    target.getId(),
                    operator.getBranchId()
            )).willReturn(Optional.of(target));
        }
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(target.getId()))
                .willReturn(Optional.empty());
        given(clock.instant()).willReturn(NOW);
    }

    /** Everything up to, but not including, the session being created. */
    private void givenManualCheckInReachesCreation(Member operator, Member target, Instant checkedInAt) {
        givenLocksAreHeld(operator, target);
        given(studyPresenceSessionRepository.findOverlappingByMemberId(target.getId(), checkedInAt, NOW))
                .willReturn(List.of());
    }

    private void givenManualCheckInIsPossible(Member operator, Member target, Instant checkedInAt) {
        givenManualCheckInReachesCreation(operator, target, checkedInAt);
        given(studyPresenceSessionRepository.save(any(StudyPresenceSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private Member createMember(Long id, Long branchId, MemberRole role) {
        Member member = new Member(
                branchId,
                "김회원",
                "password",
                role,
                10,
                LocalDate.of(2026, 8, 1),
                3L
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
