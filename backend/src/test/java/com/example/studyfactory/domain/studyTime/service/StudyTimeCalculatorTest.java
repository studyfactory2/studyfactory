package com.example.studyfactory.domain.studyTime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.studyfactory.domain.studyTime.model.BreakStudyInterval;
import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.model.StudyTimeCalculationInput;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
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
    void exposesEveryCanonicalBreakBetweenStudyPeriods() {
        assertEquals(6, StudyBreak.values().length);
        assertBreak(StudyBreak.AFTER_FIRST, StudyPeriod.FIRST, StudyPeriod.SECOND, LocalTime.of(10, 30), LocalTime.of(10, 45));
        assertBreak(StudyBreak.LUNCH, StudyPeriod.SECOND, StudyPeriod.THIRD, LocalTime.of(12, 5), LocalTime.of(13, 20));
        assertBreak(StudyBreak.AFTER_THIRD, StudyPeriod.THIRD, StudyPeriod.FOURTH, LocalTime.of(14, 30), LocalTime.of(14, 45));
        assertBreak(StudyBreak.AFTER_FOURTH, StudyPeriod.FOURTH, StudyPeriod.FIFTH, LocalTime.of(16, 15), LocalTime.of(16, 30));
        assertBreak(StudyBreak.DINNER, StudyPeriod.FIFTH, StudyPeriod.SIXTH, LocalTime.of(17, 50), LocalTime.of(19, 5));
        assertBreak(StudyBreak.AFTER_SIXTH, StudyPeriod.SIXTH, StudyPeriod.SEVENTH, LocalTime.of(20, 25), LocalTime.of(20, 40));

        Duration totalBreakDuration = Arrays.stream(StudyBreak.values())
                .map(StudyBreak::getDuration)
                .reduce(Duration.ZERO, Duration::plus);

        assertEquals(Duration.ofMinutes(210), totalBreakDuration);
    }

    @Test
    void countsAFullDayWithoutCountingScheduledBreaks() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyInterval(atSeoul(STUDY_DATE, 8, 0), atSeoul(STUDY_DATE, 23, 0))
        );

        assertEquals(Duration.ofMinutes(570), result.totalDuration());
        assertEquals(Duration.ofMinutes(570), result.periodDuration());
        assertEquals(Duration.ZERO, result.breakDuration());
        assertEquals(6, result.breaks().size());
        result.breaks().forEach(studyBreak -> assertEquals(Duration.ZERO, studyBreak.duration()));
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
    void countsOnlyTheExplicitBreakStudyTimeThatOverlapsPresence() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 10, 30),
                                atSeoul(STUDY_DATE, 10, 45)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_FIRST,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 10, 35),
                                        atSeoul(STUDY_DATE, 10, 42)
                                )
                        )),
                        EnumSet.noneOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ZERO, result.periodDuration());
        assertEquals(Duration.ofMinutes(7), result.breakDuration());
        assertEquals(Duration.ofMinutes(7), result.totalDuration());
        assertEquals(Duration.ofMinutes(7), breakDuration(result, StudyBreak.AFTER_FIRST));
    }

    @Test
    void stopsBreakStudyCountingAtTheMemberCheckoutTime() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 9, 0),
                                atSeoul(STUDY_DATE, 10, 40)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_FIRST,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 10, 35),
                                        atSeoul(STUDY_DATE, 10, 45)
                                )
                        )),
                        EnumSet.noneOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ofMinutes(90), result.periodDuration());
        assertEquals(Duration.ofMinutes(5), result.breakDuration());
        assertEquals(Duration.ofMinutes(95), result.totalDuration());
    }

    @Test
    void mergesRestartedBreakActivationsWithoutDoubleCounting() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 10, 30),
                                atSeoul(STUDY_DATE, 10, 45)
                        )),
                        List.of(
                                new BreakStudyInterval(
                                        StudyBreak.AFTER_FIRST,
                                        new StudyInterval(
                                                atSeoul(STUDY_DATE, 10, 30),
                                                atSeoul(STUDY_DATE, 10, 40)
                                        )
                                ),
                                new BreakStudyInterval(
                                        StudyBreak.AFTER_FIRST,
                                        new StudyInterval(
                                                atSeoul(STUDY_DATE, 10, 35),
                                                atSeoul(STUDY_DATE, 10, 45)
                                        )
                                )
                        ),
                        EnumSet.noneOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ofMinutes(15), result.breakDuration());
        assertEquals(Duration.ofMinutes(15), result.totalDuration());
    }

    @Test
    void excludesABreakWhenBothNeighboringPeriodsAreOnLeave() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 10, 30),
                                atSeoul(STUDY_DATE, 10, 45)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_FIRST,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 10, 30),
                                        atSeoul(STUDY_DATE, 10, 45)
                                )
                        )),
                        EnumSet.of(StudyPeriod.FIRST, StudyPeriod.SECOND)
                )
        );

        assertEquals(Duration.ZERO, result.breakDuration());
        assertEquals(Duration.ZERO, result.totalDuration());
    }

    @Test
    void countsEveryExplicitlyStudiedBreakForAFullAuthorizedDay() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 8, 0),
                                atSeoul(STUDY_DATE, 23, 0)
                        )),
                        Arrays.stream(StudyBreak.values())
                                .map(studyBreak -> new BreakStudyInterval(
                                        studyBreak,
                                        new StudyInterval(
                                                atSeoul(STUDY_DATE, 9, 0),
                                                atSeoul(STUDY_DATE, 22, 0)
                                        )
                                ))
                                .toList(),
                        EnumSet.noneOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ofMinutes(570), result.periodDuration());
        assertEquals(Duration.ofMinutes(210), result.breakDuration());
        assertEquals(Duration.ofMinutes(780), result.totalDuration());
    }

    @Test
    void excludesAllNormalAndBreakStudyTimeOnFullLeave() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 8, 0),
                                atSeoul(STUDY_DATE, 23, 0)
                        )),
                        Arrays.stream(StudyBreak.values())
                                .map(studyBreak -> new BreakStudyInterval(
                                        studyBreak,
                                        new StudyInterval(
                                                atSeoul(STUDY_DATE, 9, 0),
                                                atSeoul(STUDY_DATE, 22, 0)
                                        )
                                ))
                                .toList(),
                        EnumSet.allOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ZERO, result.periodDuration());
        assertEquals(Duration.ZERO, result.breakDuration());
        assertEquals(Duration.ZERO, result.totalDuration());
    }

    @Test
    void preservesMorningLeavePeriodsOneThroughFourAndAllowsTheTransitionBreak() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 8, 0),
                                atSeoul(STUDY_DATE, 23, 0)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_FOURTH,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 16, 15),
                                        atSeoul(STUDY_DATE, 16, 30)
                                )
                        )),
                        EnumSet.of(
                                StudyPeriod.FIRST,
                                StudyPeriod.SECOND,
                                StudyPeriod.THIRD,
                                StudyPeriod.FOURTH
                        )
                )
        );

        assertEquals(Duration.ofMinutes(240), result.periodDuration());
        assertEquals(Duration.ofMinutes(15), result.breakDuration());
        assertEquals(Duration.ofMinutes(255), result.totalDuration());
        assertEquals(Duration.ZERO, periodDuration(result, StudyPeriod.FOURTH));
        assertEquals(Duration.ofMinutes(80), periodDuration(result, StudyPeriod.FIFTH));
    }

    @Test
    void preservesAfternoonLeavePeriodsFourThroughSevenAndAllowsTheTransitionBreak() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 8, 0),
                                atSeoul(STUDY_DATE, 23, 0)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_THIRD,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 14, 30),
                                        atSeoul(STUDY_DATE, 14, 45)
                                )
                        )),
                        EnumSet.of(
                                StudyPeriod.FOURTH,
                                StudyPeriod.FIFTH,
                                StudyPeriod.SIXTH,
                                StudyPeriod.SEVENTH
                        )
                )
        );

        assertEquals(Duration.ofMinutes(240), result.periodDuration());
        assertEquals(Duration.ofMinutes(15), result.breakDuration());
        assertEquals(Duration.ofMinutes(255), result.totalDuration());
        assertEquals(Duration.ofMinutes(70), periodDuration(result, StudyPeriod.THIRD));
        assertEquals(Duration.ZERO, periodDuration(result, StudyPeriod.FOURTH));
    }

    @Test
    void anUnclosedBreakActivationCannotCreditLaterBreaks() {
        DailyStudyTime result = calculator.calculate(
                STUDY_DATE,
                new StudyTimeCalculationInput(
                        List.of(new StudyInterval(
                                atSeoul(STUDY_DATE, 8, 0),
                                atSeoul(STUDY_DATE, 23, 0)
                        )),
                        List.of(new BreakStudyInterval(
                                StudyBreak.AFTER_FIRST,
                                new StudyInterval(
                                        atSeoul(STUDY_DATE, 10, 35),
                                        atSeoul(STUDY_DATE, 19, 5)
                                )
                        )),
                        EnumSet.noneOf(StudyPeriod.class)
                )
        );

        assertEquals(Duration.ofMinutes(10), result.breakDuration());
        assertEquals(Duration.ofMinutes(10), breakDuration(result, StudyBreak.AFTER_FIRST));
        assertEquals(Duration.ZERO, breakDuration(result, StudyBreak.LUNCH));
        assertEquals(Duration.ZERO, breakDuration(result, StudyBreak.DINNER));
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

    private Duration breakDuration(DailyStudyTime result, StudyBreak studyBreak) {
        return result.breaks().stream()
                .filter(breakStudyTime -> breakStudyTime.studyBreak() == studyBreak)
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

    private void assertBreak(
            StudyBreak studyBreak,
            StudyPeriod previousPeriod,
            StudyPeriod nextPeriod,
            LocalTime startTime,
            LocalTime endTime
    ) {
        assertEquals(previousPeriod, studyBreak.getPreviousPeriod());
        assertEquals(nextPeriod, studyBreak.getNextPeriod());
        assertEquals(startTime, studyBreak.getStartTime());
        assertEquals(endTime, studyBreak.getEndTime());
    }

    private Instant atSeoul(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
                .atZone(StudyTimeCalculator.STUDY_ZONE)
                .toInstant();
    }
}
