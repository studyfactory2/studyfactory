package com.example.studyfactory.domain.studyPresence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("학습실 입퇴실 기록 엔티티 테스트")
class StudyPresenceSessionTest {

    private static final Instant CHECKED_IN_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    @DisplayName("입실 기록은 회원과 지점, 서버 입실 시간을 보관한다")
    void createActiveSession() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);

        assertThat(session.getMemberId()).isEqualTo(1L);
        assertThat(session.getBranchId()).isEqualTo(2L);
        assertThat(session.getCheckedInAt()).isEqualTo(CHECKED_IN_AT);
        assertThat(session.getCheckedOutAt()).isNull();
        assertThat(session.getCloseReason()).isNull();
        assertThat(session.getActiveMemberId()).isEqualTo(1L);
        assertThat(session.getClosedByMemberId()).isNull();
        assertThat(session.isAutomaticallyClosed()).isFalse();
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("퇴실하면 퇴실 시간을 기록하고 활성 회원 키를 비운다")
    void checkOut() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);
        Instant checkedOutAt = Instant.parse("2026-08-28T09:00:00Z");

        session.checkOut(checkedOutAt);

        assertThat(session.getCheckedOutAt()).isEqualTo(checkedOutAt);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 수동 퇴실은 처리한 관리자 식별자를 감사 정보로 남긴다")
    void managerCheckOut() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);
        Instant checkedOutAt = CHECKED_IN_AT.plusSeconds(60);

        session.managerCheckOut(checkedOutAt, 9L);

        assertThat(session.getCheckedOutAt()).isEqualTo(checkedOutAt);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getClosedByMemberId()).isEqualTo(9L);
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("자정 자동 퇴실은 시스템 처리 여부를 감사 정보로 남긴다")
    void automaticallyCheckOut() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);
        Instant midnight = CHECKED_IN_AT.plusSeconds(60);

        session.automaticallyCheckOut(midnight);

        assertThat(session.getCheckedOutAt()).isEqualTo(midnight);
        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.CHECK_OUT);
        assertThat(session.getClosedByMemberId()).isNull();
        assertThat(session.isAutomaticallyClosed()).isTrue();
        assertThat(session.getActiveMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("입실 시간보다 빠른 시간으로 퇴실할 수 없다")
    void rejectCheckoutBeforeCheckIn() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);

        assertThatThrownBy(() -> session.checkOut(CHECKED_IN_AT.minusSeconds(1)))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("퇴실 시간은 입실 시간보다 빠를 수 없습니다.");
    }

    @Test
    @DisplayName("이미 닫힌 입실 기록을 다시 퇴실 처리할 수 없다")
    void rejectRepeatedCheckout() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);
        session.checkOut(CHECKED_IN_AT.plusSeconds(1));

        assertThatThrownBy(() -> session.checkOut(CHECKED_IN_AT.plusSeconds(2)))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("이미 퇴실 처리된 기록입니다.");
    }

    @Test
    @DisplayName("사원 삭제로 닫힌 기록은 실제 퇴실과 구별한다")
    void closeForMemberDeletion() {
        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(1L, 2L, CHECKED_IN_AT);

        session.closeForMemberDeletion(CHECKED_IN_AT.plusSeconds(1));

        assertThat(session.getCloseReason()).isEqualTo(StudyPresenceCloseReason.MEMBER_DELETED);
        assertThat(session.getClosedByMemberId()).isNull();
        assertThat(session.isActive()).isFalse();
    }
}
