package com.example.studyfactory.domain.studyBreak.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.studyBreak.model.StudyBreakWindow;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("휴식시간 구간 정책 테스트")
class StudyBreakWindowPolicyTest {

    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);

    private final StudyBreakWindowPolicy policy = new StudyBreakWindowPolicy();

    @Test
    @DisplayName("서울 기준 여섯 개 휴식시간의 시작과 종료를 정확히 계산한다")
    void exposeAllSixCanonicalSeoulWindows() {
        List<ExpectedWindow> expectedWindows = List.of(
                new ExpectedWindow(StudyBreak.AFTER_FIRST, LocalTime.of(10, 30), LocalTime.of(10, 45)),
                new ExpectedWindow(StudyBreak.LUNCH, LocalTime.of(12, 5), LocalTime.of(13, 20)),
                new ExpectedWindow(StudyBreak.AFTER_THIRD, LocalTime.of(14, 30), LocalTime.of(14, 45)),
                new ExpectedWindow(StudyBreak.AFTER_FOURTH, LocalTime.of(16, 15), LocalTime.of(16, 30)),
                new ExpectedWindow(StudyBreak.DINNER, LocalTime.of(17, 50), LocalTime.of(19, 5)),
                new ExpectedWindow(StudyBreak.AFTER_SIXTH, LocalTime.of(20, 25), LocalTime.of(20, 40))
        );

        assertThat(StudyBreak.values()).hasSize(expectedWindows.size());
        for (ExpectedWindow expected : expectedWindows) {
            StudyBreakWindow window = policy.windowFor(STUDY_DATE, expected.studyBreak());

            assertThat(window.studyDate()).as(expected.studyBreak() + " date").isEqualTo(STUDY_DATE);
            assertThat(window.studyBreak()).isEqualTo(expected.studyBreak());
            assertThat(window.startedAt().atZone(StudyBreakWindowPolicy.STUDY_ZONE).toLocalTime())
                    .as(expected.studyBreak() + " start")
                    .isEqualTo(expected.startedAt());
            assertThat(window.endedAt().atZone(StudyBreakWindowPolicy.STUDY_ZONE).toLocalTime())
                    .as(expected.studyBreak() + " end")
                    .isEqualTo(expected.endedAt());
            assertThat(window.startedAt().atZone(StudyBreakWindowPolicy.STUDY_ZONE).toLocalDate())
                    .isEqualTo(STUDY_DATE);
            assertThat(window.endedAt().atZone(StudyBreakWindowPolicy.STUDY_ZONE).toLocalDate())
                    .isEqualTo(STUDY_DATE);
        }

        assertThat(policy.windowFor(STUDY_DATE, StudyBreak.AFTER_FIRST).startedAt())
                .isEqualTo(Instant.parse("2026-08-28T01:30:00Z"));
        assertThat(policy.windowFor(STUDY_DATE, StudyBreak.AFTER_SIXTH).endedAt())
                .isEqualTo(Instant.parse("2026-08-28T11:40:00Z"));
    }

    @Test
    @DisplayName("여섯 개 휴식시간 모두 시작은 포함하고 종료는 제외한다")
    void resolveEveryWindowWithHalfOpenBoundaries() {
        for (StudyBreak studyBreak : StudyBreak.values()) {
            StudyBreakWindow window = policy.windowFor(STUDY_DATE, studyBreak);

            assertThat(policy.findCurrent(window.startedAt()))
                    .as(studyBreak + " at start")
                    .map(StudyBreakWindow::studyBreak)
                    .contains(studyBreak);
            assertThat(policy.findCurrent(window.endedAt().minusNanos(1)))
                    .as(studyBreak + " immediately before end")
                    .map(StudyBreakWindow::studyBreak)
                    .contains(studyBreak);
            assertThat(policy.findCurrent(window.startedAt().minusNanos(1)))
                    .as(studyBreak + " immediately before start")
                    .isEmpty();
            assertThat(policy.findCurrent(window.endedAt()))
                    .as(studyBreak + " at end")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("서울 자정을 기준으로 공부 날짜를 변경한다")
    void deriveStudyDateAtSeoulMidnight() {
        assertThat(policy.studyDateAt(Instant.parse("2026-08-28T14:59:59.999999999Z")))
                .isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(policy.studyDateAt(Instant.parse("2026-08-28T15:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    @DisplayName("교시 중과 일과 시작 전·종료 후에는 현재 휴식시간이 없다")
    void returnEmptyOutsideBreakWindows() {
        assertThat(policy.findCurrent(atSeoul(STUDY_DATE, 8, 59))).isEmpty();
        assertThat(policy.findCurrent(atSeoul(STUDY_DATE, 10, 0))).isEmpty();
        assertThat(policy.findCurrent(atSeoul(STUDY_DATE, 15, 0))).isEmpty();
        assertThat(policy.findCurrent(atSeoul(STUDY_DATE, 22, 0))).isEmpty();
    }

    private Instant atSeoul(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(StudyBreakWindowPolicy.STUDY_ZONE).toInstant();
    }

    private record ExpectedWindow(
            StudyBreak studyBreak,
            LocalTime startedAt,
            LocalTime endedAt
    ) {
    }
}
