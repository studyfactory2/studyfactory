package com.example.studyfactory.domain.studyTime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyTimeCalculatorTest {

    private static final LocalDate STUDY_DATE = LocalDate.of(2026, 8, 28);

    private final StudyTimeCalculator calculator = new StudyTimeCalculator();

    @Test
    void exposesTheCanonicalSevenPeriodTimetable() {
        assertEquals(7, StudyPeriod.values().length);
        assertPeriod(StudyPeriod.FIRST, 1, 0, LocalTime.of(9, 0), LocalTime.of(10, 30));
        assertPeriod(StudyPeriod.SECOND, 2, 1, LocalTime.of(10, 45), LocalTime.of(12, 5));
        assertPeriod(StudyPeriod.THIRD, 3, 2, LocalTime.of(13, 20), LocalTime.of(14, 30));
        assertPeriod(StudyPeriod.FOURTH, 4, 3, LocalTime.of(14, 45), LocalTime.of(16, 15));
        assertPeriod(StudyPeriod.FIFTH, 5, 4, LocalTime.of(16, 30), LocalTime.of(17, 50));
        assertPeriod(StudyPeriod.SIXTH, 6, 5, LocalTime.of(19, 5), LocalTime.of(20, 25));
        assertPeriod(StudyPeriod.SEVENTH, 7, 6, LocalTime.of(20, 40), LocalTime.of(22, 0));

        Duration totalPeriodDuration = Arrays.stream(StudyPeriod.values())
                .map(StudyPeriod::getDuration)
                .reduce(Duration.ZERO, Duration::plus);

        assertEquals(Duration.ofMinutes(570), totalPeriodDuration);
    }

    @Test
    void countsAFullDayWithoutCountingScheduledBreaks() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 8, 0), atSeoul(STUDY_DATE, 23, 0))
        );

        assertEquals(Duration.ofMinutes(570), result.totalDuration());
        assertEquals(Duration.ofMinutes(90), periodDuration(result, StudyPeriod.FIRST));
        assertEquals(Duration.ofMinutes(80), periodDuration(result, StudyPeriod.SECOND));
        assertEquals(Duration.ofMinutes(70), periodDuration(result, StudyPeriod.THIRD));
        assertEquals(Duration.ofMinutes(90), periodDuration(result, StudyPeriod.FOURTH));
        assertEquals(Duration.ofMinutes(80), periodDuration(result, StudyPeriod.FIFTH));
        assertEquals(Duration.ofMinutes(80), periodDuration(result, StudyPeriod.SIXTH));
        assertEquals(Duration.ofMinutes(80), periodDuration(result, StudyPeriod.SEVENTH));
    }

    @Test
    void clipsStudyTimeToCheckInAndCheckOutTimes() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 9, 20), atSeoul(STUDY_DATE, 12, 0))
        );

        assertEquals(Duration.ofMinutes(145), result.totalDuration());
        assertEquals(Duration.ofMinutes(70), periodDuration(result, StudyPeriod.FIRST));
        assertEquals(Duration.ofMinutes(75), periodDuration(result, StudyPeriod.SECOND));
        assertEquals(Duration.ZERO, periodDuration(result, StudyPeriod.THIRD));
    }

    @Test
    void excludesLunchWhileCountingBothSurroundingPeriods() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 11, 50), atSeoul(STUDY_DATE, 13, 30))
        );

        assertEquals(Duration.ofMinutes(25), result.totalDuration());
        assertEquals(Duration.ofMinutes(15), periodDuration(result, StudyPeriod.SECOND));
        assertEquals(Duration.ofMinutes(10), periodDuration(result, StudyPeriod.THIRD));
    }

    @Test
    void returnsZeroForBreakOnlyAndZeroLengthIntervals() {
        DailyStudyTime breakOnly = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 12, 5), atSeoul(STUDY_DATE, 13, 20))
        );
        DailyStudyTime zeroLength = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 9, 30), atSeoul(STUDY_DATE, 9, 30))
        );

        assertEquals(Duration.ZERO, breakOnly.totalDuration());
        assertEquals(Duration.ZERO, zeroLength.totalDuration());
    }

    @Test
    void returnsAllZeroPeriodsWhenThereAreNoIntervals() {
        DailyStudyTime result = calculator.calculate(STUDY_DATE, List.of());

        assertEquals(Duration.ZERO, result.totalDuration());
        assertEquals(7, result.periods().size());
        result.periods().forEach(period -> assertEquals(Duration.ZERO, period.duration()));
    }

    @Test
    void doesNotDoubleCountOverlappingIntervals() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                List.of(
                        new StudyInterval(atSeoul(STUDY_DATE, 9, 0), atSeoul(STUDY_DATE, 10, 0)),
                        new StudyInterval(atSeoul(STUDY_DATE, 9, 30), atSeoul(STUDY_DATE, 10, 30))
                )
        );

        assertEquals(Duration.ofMinutes(90), result.totalDuration());
        assertEquals(Duration.ofMinutes(90), periodDuration(result, StudyPeriod.FIRST));
    }

    @Test
    void clipsCrossMidnightIntervalsToTheRequestedSeoulDate() {
        LocalDate previousDate = STUDY_DATE.minusDays(1);
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(previousDate, 21, 30), atSeoul(STUDY_DATE, 9, 30))
        );

        assertEquals(Duration.ofMinutes(30), result.totalDuration());
        assertEquals(Duration.ofMinutes(30), periodDuration(result, StudyPeriod.FIRST));
    }

    @Test
    void rejectsAnIntervalWhoseEndIsBeforeItsStart() {
        Instant later = atSeoul(STUDY_DATE, 10, 0);
        Instant earlier = atSeoul(STUDY_DATE, 9, 0);

        assertThrows(IllegalArgumentException.class, () -> new StudyInterval(later, earlier));
    }

    private Duration periodDuration(DailyStudyTime result, StudyPeriod period) {
        return result.periods().stream()
                .filter(periodStudyTime -> periodStudyTime.period() == period)
                .findFirst()
                .orElseThrow()
                .duration();
    }

    private void assertPeriod(
            StudyPeriod period,
            int periodNumber,
            int weeklyPlanIndex,
            LocalTime startTime,
            LocalTime endTime
    ) {
        assertEquals(periodNumber, period.getPeriodNumber());
        assertEquals(weeklyPlanIndex, period.getWeeklyPlanIndex());
        assertEquals(startTime, period.getStartTime());
        assertEquals(endTime, period.getEndTime());
    }

    private Instant atSeoul(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
                .atZone(StudyTimeCalculator.STUDY_ZONE)
                .toInstant();
    }
}
