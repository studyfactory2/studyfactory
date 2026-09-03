package com.example.studyfactory.domain.studyPresence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.service.StudyBreakLifecycleService;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceCheckoutMethod;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceDoorQrResponse;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCloseReason;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyPresence.qr.StudyPresenceQrTokenProvider;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
@DisplayName("학습실 입퇴실 서비스 테스트")
class StudyPresenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final Instant AFTER_SEOUL_MIDNIGHT = Instant.parse("2026-08-28T15:00:05Z");
    private static final Instant PREVIOUS_SEOUL_DAY_CHECK_IN = Instant.parse("2026-08-28T14:00:00Z");
    private static final Instant SEOUL_MIDNIGHT = Instant.parse("2026-08-28T15:00:00Z");
    private static final String QR_TOKEN = "signed-qr-token";

    @InjectMocks
    private StudyPresenceService studyPresenceService;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

    @Spy
    private StudyPresenceAutoClosePolicy autoClosePolicy = new StudyPresenceAutoClosePolicy(true);

    @Mock
    private StudyBreakLifecycleService studyBreakLifecycleService;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("관리자는 현재 소속 지점의 영구 출입 QR을 조회한다")
    void findDoorQrForAdminBranch() {
        Member admin = createMember(1L, 2L, MemberRole.ADMIN);
        given(memberRepository.findById(1L)).willReturn(Optional.of(admin));
        given(studyPresenceQrTokenProvider.createToken(2L)).willReturn(QR_TOKEN);

        StudyPresenceDoorQrResponse response = studyPresenceService.findDoorQr(1L);

        assertThat(response.branchId()).isEqualTo(2L);
        assertThat(response.qrToken()).isEqualTo(QR_TOKEN);
        then(memberRepository).should().findById(1L);
        then(studyPresenceQrTokenProvider).should().createToken(2L);
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("스태프는 관리자용 출입 QR을 조회할 수 없다")
    void rejectDoorQrForStaff() {
        Member staff = createMember(1L, 2L, MemberRole.STAFF);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));

        assertThatThrownBy(() -> studyPresenceService.findDoorQr(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        then(studyPresenceQrTokenProvider).shouldHaveNoInteractions();
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일반 회원은 관리자용 출입 QR을 조회할 수 없다")
    void rejectDoorQrForMember() {
        Member member = createMember(1L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> studyPresenceService.findDoorQr(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        then(studyPresenceQrTokenProvider).shouldHaveNoInteractions();
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제되었거나 존재하지 않는 관리자는 출입 QR을 조회할 수 없다")
    void rejectDoorQrForMissingMember() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.findDoorQr(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");

        then(studyPresenceQrTokenProvider).shouldHaveNoInteractions();
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원 행을 잠그고 서버 시간과 현재 지점으로 입실 기록을 만든다")
    void checkIn() {
        Member member = createMember(1L, 2L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L)).willReturn(Optional.empty());
        given(clock.instant()).willReturn(NOW);
        given(studyPresenceSessionRepository.save(any(StudyPresenceSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        StudyPresenceSession session = studyPresenceService.checkIn(1L, QR_TOKEN);

        assertThat(session.getMemberId()).isEqualTo(1L);
        assertThat(session.getBranchId()).isEqualTo(2L);
        assertThat(session.getCheckedInAt()).isEqualTo(NOW);
        assertThat(session.isActive()).isTrue();
        then(memberRepository).should().findByIdForUpdate(1L);
        then(studyPresenceSessionRepository).should().save(session);
    }

    @Test
    @DisplayName("존재하지 않는 사원은 입실할 수 없다")
    void rejectCheckInForMissingMember() {
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L, QR_TOKEN))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");

        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 입실 중인 회원은 중복 입실할 수 없다")
    void rejectDuplicateCheckIn() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("이미 입실 처리된 회원입니다.");

        then(studyPresenceSessionRepository).should().findActiveByMemberIdForUpdate(1L);
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("새 날짜의 입실은 어제 미퇴실 기록을 자정으로 닫은 뒤 새 기록을 만든다")
    void checkInAfterAutomaticallyClosingPreviousDaySession() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession staleSession = StudyPresenceSession.qrCheckIn(
                1L,
                2L,
                PREVIOUS_SEOUL_DAY_CHECK_IN
        );
        ReflectionTestUtils.setField(staleSession, "id", 10L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(staleSession));
        given(clock.instant()).willReturn(AFTER_SEOUL_MIDNIGHT);
        given(studyPresenceSessionRepository.save(any(StudyPresenceSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        StudyPresenceSession newSession = studyPresenceService.checkIn(1L, QR_TOKEN);

        assertThat(staleSession.getCheckedOutAt()).isEqualTo(SEOUL_MIDNIGHT);
        assertThat(staleSession.isAutomaticallyClosed()).isTrue();
        assertThat(newSession.getCheckedInAt()).isEqualTo(AFTER_SEOUL_MIDNIGHT);
        assertThat(newSession.isActive()).isTrue();
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(1L, 10L, SEOUL_MIDNIGHT);
        then(studyPresenceSessionRepository).should().flush();
        then(studyPresenceSessionRepository).should().save(newSession);
    }

    @Test
    @DisplayName("회원 행을 잠그고 활성 입실 기록을 서버 시간으로 퇴실 처리한다")
    void checkOut() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(activeSession, "id", 10L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        StudyPresenceSession session = studyPresenceService.checkOut(1L, QR_TOKEN);

        assertThat(session.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
        then(studyBreakLifecycleService).should().closeForPresenceEnd(1L, 10L, NOW);
        then(memberRepository).should().findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("자정이 지난 어제 기록은 QR 요청이 먼저 와도 자정 자동 퇴실로 정규화한다")
    void normalizeStaleQrCheckoutToMidnight() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession staleSession = StudyPresenceSession.qrCheckIn(
                1L,
                2L,
                PREVIOUS_SEOUL_DAY_CHECK_IN
        );
        ReflectionTestUtils.setField(staleSession, "id", 10L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(staleSession));
        given(clock.instant()).willReturn(AFTER_SEOUL_MIDNIGHT);

        StudyPresenceSession session = studyPresenceService.checkOut(1L, QR_TOKEN);

        assertThat(session.getCheckedOutAt()).isEqualTo(SEOUL_MIDNIGHT);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getClosedByMemberId()).isNull();
        assertThat(session.isAutomaticallyClosed()).isTrue();
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(1L, 10L, SEOUL_MIDNIGHT);
    }

    @Test
    @DisplayName("활성 입실 기록이 없으면 퇴실 처리할 수 없다")
    void rejectCheckoutWithoutActiveSession() {
        Member member = createMember(1L, 2L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.checkOut(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("입실 중인 기록이 없습니다.");
    }

    @Test
    @DisplayName("다른 지점 QR로 입실할 수 없다")
    void rejectCheckInWithAnotherBranchQr() {
        Member member = createMember(1L, 2L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(3L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("소속 지점의 출입 QR 코드만 사용할 수 있습니다.");

        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입실한 지점과 다른 지점 QR로 퇴실할 수 없다")
    void rejectCheckoutWithAnotherBranchQr() {
        Member member = createMember(1L, 3L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(3L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> studyPresenceService.checkOut(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("입실한 지점의 출입 QR 코드만 사용할 수 있습니다.");

        assertThat(activeSession.isActive()).isTrue();
    }

    @Test
    @DisplayName("지점이 변경되어도 입실 당시 지점 QR로 퇴실할 수 있다")
    void checkOutAtOriginalBranchAfterBranchReassignment() {
        Member reassignedMember = createMember(1L, 3L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(reassignedMember));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        StudyPresenceSession session = studyPresenceService.checkOut(1L, QR_TOKEN);

        assertThat(session.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("유효하지 않은 QR은 회원 행을 조회하거나 잠그기 전에 거절한다")
    void rejectInvalidQrBeforeMemberLookup() {
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN))
                .willThrow(StudyPresenceException.invalidQrToken());

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("유효하지 않은 출입 QR 코드입니다.");

        then(memberRepository).shouldHaveNoInteractions();
        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("현재 회원의 활성 입실 기록을 조회한다")
    void findActive() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        Optional<StudyPresenceSession> session = studyPresenceService.findActive(1L);

        assertThat(session).contains(activeSession);
    }

    @Test
    @DisplayName("자동 종료 대기 중인 어제 기록은 현재 입실 상태로 노출하지 않는다")
    void hideStaleSessionFromCurrentStatus() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession staleSession = StudyPresenceSession.qrCheckIn(
                1L,
                2L,
                PREVIOUS_SEOUL_DAY_CHECK_IN
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(staleSession));
        given(clock.instant()).willReturn(AFTER_SEOUL_MIDNIGHT);

        Optional<StudyPresenceSession> session = studyPresenceService.findActive(1L);

        assertThat(session).isEmpty();
    }

    @Test
    @DisplayName("사원 삭제 시 활성 기록만 닫고 입퇴실 이력은 보존한다")
    void closeActiveSessionForMemberDeletion() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(activeSession, "id", 10L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        studyPresenceService.closeActiveSessionForMemberDeletion(1L);

        assertThat(activeSession.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(activeSession.getCloseReason()).isEqualTo(StudyPresenceCloseReason.MEMBER_DELETED);
        assertThat(activeSession.isActive()).isFalse();
        then(studyBreakLifecycleService).should().closeForPresenceEnd(1L, 10L, NOW);
        then(memberRepository).should().findByIdForUpdate(1L);
        then(studyPresenceSessionRepository).should().findActiveByMemberIdForUpdate(1L);
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("자정이 지난 어제 기록은 회원 삭제보다 자정 자동 퇴실을 우선 보존한다")
    void normalizeStaleDeletionClosureToMidnight() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession staleSession = StudyPresenceSession.qrCheckIn(
                1L,
                2L,
                PREVIOUS_SEOUL_DAY_CHECK_IN
        );
        ReflectionTestUtils.setField(staleSession, "id", 10L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findActiveByMemberIdForUpdate(1L))
                .willReturn(Optional.of(staleSession));
        given(clock.instant()).willReturn(AFTER_SEOUL_MIDNIGHT);

        studyPresenceService.closeActiveSessionForMemberDeletion(1L);

        assertThat(staleSession.getCheckedOutAt()).isEqualTo(SEOUL_MIDNIGHT);
        assertThat(staleSession.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(staleSession.isAutomaticallyClosed()).isTrue();
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(1L, 10L, SEOUL_MIDNIGHT);
    }

    @Test
    @DisplayName("관리자와 스태프는 같은 지점의 선택된 입실 기록을 서버 시간으로 퇴실 처리한다")
    void managerCheckOut() {
        Member manager = createMember(9L, 2L, MemberRole.STAFF);
        Member targetMember = createMember(1L, 2L);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(27_738));
        ReflectionTestUtils.setField(activeSession, "id", 10L);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findByIdAndBranchIdForUpdate(10L, 2L))
                .willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetMember));

        var response = studyPresenceService.managerCheckOut(9L, 10L);

        assertThat(response.closeReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(response.closedByMemberId()).isEqualTo(9L);
        assertThat(response.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.MANAGER);
        assertThat(response.currentlyActive()).isFalse();
        assertThat(response.checkedOutAt()).isEqualTo(NOW);
        assertThat(response.presenceDuration().totalSeconds()).isEqualTo(27_738);
        assertThat(response.presenceDuration().formatted()).isEqualTo("07:42:18");
        then(studyBreakLifecycleService).should().closeForPresenceEnd(1L, 10L, NOW);
    }

    @Test
    @DisplayName("자정이 지난 어제 기록은 관리자 요청이 먼저 와도 자정 자동 퇴실로 정규화한다")
    void normalizeStaleManagerCheckoutToMidnight() {
        Member manager = createMember(9L, 2L, MemberRole.STAFF);
        Member targetMember = createMember(1L, 2L);
        StudyPresenceSession staleSession = StudyPresenceSession.qrCheckIn(
                1L,
                2L,
                PREVIOUS_SEOUL_DAY_CHECK_IN
        );
        ReflectionTestUtils.setField(staleSession, "id", 10L);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findByIdAndBranchIdForUpdate(10L, 2L))
                .willReturn(Optional.of(staleSession));
        given(clock.instant()).willReturn(AFTER_SEOUL_MIDNIGHT);
        given(memberRepository.findById(1L)).willReturn(Optional.of(targetMember));

        var response = studyPresenceService.managerCheckOut(9L, 10L);

        assertThat(response.checkedOutAt()).isEqualTo(SEOUL_MIDNIGHT);
        assertThat(response.closedByMemberId()).isNull();
        assertThat(response.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.AUTO_MIDNIGHT);
        assertThat(response.currentlyActive()).isFalse();
        then(studyBreakLifecycleService).should()
                .closeForPresenceEnd(1L, 10L, SEOUL_MIDNIGHT);
    }

    @Test
    @DisplayName("일반 회원은 다른 회원을 수동 퇴실 처리할 수 없다")
    void rejectManagerCheckOutForMember() {
        Member member = createMember(9L, 2L, MemberRole.MEMBER);
        given(memberRepository.findById(9L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> studyPresenceService.managerCheckOut(9L, 10L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");

        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자는 다른 지점의 입실 기록을 수동 퇴실 처리할 수 없다")
    void rejectManagerCheckOutForAnotherBranch() {
        Member manager = createMember(9L, 3L, MemberRole.ADMIN);
        StudyPresenceSession activeSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(activeSession, "id", 10L);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findByIdAndBranchIdForUpdate(10L, 3L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.managerCheckOut(9L, 10L))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("존재하지 않는 입퇴실 기록입니다.");

        assertThat(activeSession.isActive()).isTrue();
        then(clock).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 닫힌 입실 기록은 관리자가 다시 퇴실 처리할 수 없다")
    void rejectManagerCheckOutForClosedSession() {
        Member manager = createMember(9L, 2L, MemberRole.ADMIN);
        StudyPresenceSession closedSession = StudyPresenceSession.qrCheckIn(1L, 2L, NOW.minusSeconds(60));
        closedSession.checkOut(NOW.minusSeconds(30));
        ReflectionTestUtils.setField(closedSession, "id", 10L);
        given(memberRepository.findById(9L)).willReturn(Optional.of(manager));
        given(studyPresenceSessionRepository.findByIdAndBranchIdForUpdate(10L, 2L))
                .willReturn(Optional.of(closedSession));
        given(clock.instant()).willReturn(NOW);

        assertThatThrownBy(() -> studyPresenceService.managerCheckOut(9L, 10L))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("이미 퇴실 처리된 기록입니다.");
    }

    private Member createMember(Long id, Long branchId) {
        return createMember(id, branchId, MemberRole.MEMBER);
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
