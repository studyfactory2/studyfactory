package com.example.studyfactory.domain.studyPresence.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.model.StudyTimeCalculationInput;
import com.example.studyfactory.domain.studyTime.service.StudyTimeCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("체류 시간과 인정 학습 시간 계약 테스트")
class StudyPresenceDurationContractTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("입퇴실 체류 시간은 시간표와 휴무를 적용한 인정 학습 시간과 구별한다")
    void distinguishPhysicalPresenceFromOfficialStudyTime() {
        LocalDate date = LocalDate.of(2026, 8, 28);
        Instant checkedInAt = date.atTime(8, 0).atZone(SEOUL).toInstant();
        Instant checkedOutAt = date.atTime(23, 0).atZone(SEOUL).toInstant();
        StudyPresenceSession session = new StudyPresenceSession(1L, 2L, checkedInAt);
        session.checkOut(checkedOutAt);

        StudyPresenceManagerSessionResponse presence = StudyPresenceManagerSessionResponse.from(
                session,
                null,
                date.atStartOfDay(SEOUL).toInstant(),
                date.plusDays(1).atStartOfDay(SEOUL).toInstant(),
                checkedOutAt
        );
        StudyTimeCalculator calculator = new StudyTimeCalculator();
        StudyInterval interval = new StudyInterval(checkedInAt, checkedOutAt);
        var regularStudyTime = calculator.calculate(date, interval);
        var fullLeaveStudyTime = calculator.calculate(
                date,
                new StudyTimeCalculationInput(
                        List.of(interval),
                        List.of(),
                        EnumSet.allOf(StudyPeriod.class)
                )
        );

        assertThat(presence.presenceDuration().totalSeconds()).isEqualTo(54_000);
        assertThat(presence.presenceDuration().formatted()).isEqualTo("15:00:00");
        assertThat(presence.closedByMemberId()).isNull();
        assertThat(presence.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.QR);
        assertThat(regularStudyTime.totalDuration().toSeconds()).isEqualTo(34_200);
        assertThat(fullLeaveStudyTime.totalDuration()).isZero();
    }

    @Test
    @DisplayName("잘못 저장된 미래 퇴실 시간도 조회 서버 시간 이후로 계산하지 않는다")
    void capClosedSessionAtAsOf() {
        Instant checkedInAt = Instant.parse("2026-08-28T00:00:00Z");
        Instant asOf = Instant.parse("2026-08-28T01:00:00Z");
        StudyPresenceSession session = new StudyPresenceSession(1L, 2L, checkedInAt);
        session.checkOut(Instant.parse("2026-08-28T02:00:00Z"));

        StudyPresenceManagerSessionResponse response = StudyPresenceManagerSessionResponse.from(
                session,
                null,
                checkedInAt,
                Instant.parse("2026-08-29T00:00:00Z"),
                asOf
        );

        assertThat(response.overlapEndedAt()).isEqualTo(asOf);
        assertThat(response.presenceDuration().totalSeconds()).isEqualTo(3_600);
    }

    @Test
    @DisplayName("자정 자동 퇴실은 QR 퇴실과 구별해 반환한다")
    void distinguishAutomaticMidnightCheckout() {
        Instant checkedInAt = Instant.parse("2026-08-28T12:00:00Z");
        Instant midnight = Instant.parse("2026-08-28T15:00:00Z");
        StudyPresenceSession session = new StudyPresenceSession(1L, 2L, checkedInAt);
        session.automaticallyCheckOut(midnight);

        StudyPresenceManagerSessionResponse response = StudyPresenceManagerSessionResponse.from(
                session,
                null,
                checkedInAt,
                midnight,
                midnight
        );

        assertThat(response.checkedOutAt()).isEqualTo(midnight);
        assertThat(response.closedByMemberId()).isNull();
        assertThat(response.checkoutMethod()).isEqualTo(StudyPresenceCheckoutMethod.AUTO_MIDNIGHT);
        assertThat(response.currentlyActive()).isFalse();
    }
}
