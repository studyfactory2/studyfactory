package com.example.studyfactory.domain.studyTime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyTime.entity.StudyPresenceCloseReason;
import com.example.studyfactory.domain.studyTime.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyTime.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyTime.repository.StudyPresenceSessionRepository;
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

    @InjectMocks
    private StudyPresenceService studyPresenceService;

    @Mock
    private StudyPresenceSessionRepository studyPresenceSessionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("회원 행을 잠그고 서버 시간과 현재 지점으로 입실 기록을 만든다")
    void checkIn() {
        Member member = createMember(1L, 2L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.empty());
        given(clock.instant()).willReturn(NOW);
        given(studyPresenceSessionRepository.save(any(StudyPresenceSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        StudyPresenceSession session = studyPresenceService.checkIn(1L);

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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("존재하지 않는 사원입니다.");

        then(studyPresenceSessionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 입실 중인 회원은 중복 입실할 수 없다")
    void rejectDuplicateCheckIn() {
        Member member = createMember(1L, 2L);
        StudyPresenceSession activeSession = new StudyPresenceSession(1L, 2L, NOW.minusSeconds(60));
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));

        assertThatThrownBy(() -> studyPresenceService.checkIn(1L))
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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.of(activeSession));
        given(clock.instant()).willReturn(NOW);

        StudyPresenceSession session = studyPresenceService.checkOut(1L);

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
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(studyPresenceSessionRepository.findByActiveMemberId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyPresenceService.checkOut(1L))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("입실 중인 기록이 없습니다.");
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
        Member member = new Member(
                branchId,
                "김회원",
                "password",
                10,
                LocalDate.of(2026, 8, 1),
                3L
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
