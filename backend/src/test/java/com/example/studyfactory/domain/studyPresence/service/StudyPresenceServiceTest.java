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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("학습실 입퇴실 서비스 테스트")
class StudyPresenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final String QR_TOKEN = "signed-qr-token";

    @InjectMocks
    private StudyPresenceService studyPresenceService;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;

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
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.empty());
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
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("이미 입실 처리된 회원입니다.");

        then(studyPresenceSessionRepository).should().findByActiveMemberId(1L);
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("회원 행을 잠그고 활성 입실 기록을 서버 시간으로 퇴실 처리한다")
    void checkOut() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        StudyPresenceSession session = studyPresenceService.checkOut(1L, QR_TOKEN);

        assertThat(session.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
        then(memberRepository).should().findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("활성 입실 기록이 없으면 퇴실 처리할 수 없다")
    void rejectCheckoutWithoutActiveSession() {
        Member member = createMember(1L, 2L);
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.empty());

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
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(3L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> studyPresenceService.checkOut(1L, QR_TOKEN))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("입실한 지점의 출입 QR 코드만 사용할 수 있습니다.");

        assertThat(activeSession.isActive()).isTrue();
    }

    @Test
    @DisplayName("지점이 변경되어도 입실 당시 지점 QR로 퇴실할 수 있다")
    void checkOutAtOriginalBranchAfterBranchReassignment() {
        Member reassignedMember = createMember(1L, 3L);
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(studyPresenceQrTokenProvider.getBranchId(QR_TOKEN)).willReturn(2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(reassignedMember));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));
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
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));

        Optional<StudyPresenceSession> session = studyPresenceService.findActive(1L);

        assertThat(session).contains(activeSession);
    }

    @Test
    @DisplayName("사원 삭제 시 활성 기록만 닫고 입퇴실 이력은 보존한다")
    void closeActiveSessionForMemberDeletion() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        studyPresenceService.closeActiveSessionForMemberDeletion(1L);

        assertThat(activeSession.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(activeSession.getCloseReason()).isEqualTo(StudyPresenceCloseReason.MEMBER_DELETED);
        assertThat(activeSession.isActive()).isFalse();
        then(memberRepository).should().findByIdForUpdate(1L);
        then(studyPresenceSessionRepository).should().findByActiveMemberId(1L);
        then(studyPresenceSessionRepository).shouldHaveNoMoreInteractions();
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
