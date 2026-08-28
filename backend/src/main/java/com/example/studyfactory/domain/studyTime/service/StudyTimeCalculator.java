package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.studyTime.model.BreakStudyInterval;
import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import com.example.studyfactory.domain.studyTime.model.StudyTimeCalculationInput;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class StudyTimeCalculator {

    public static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");

    public DailyStudyTime calculate(LocalDate studyDate, StudyInterval interval) {
        Objects.requireNonNull(interval, "interval must not be null");
        return calculate(studyDate, List.of(interval));
    }

    public DailyStudyTime calculate(LocalDate studyDate, List<StudyInterval> intervals) {
        Objects.requireNonNull(intervals, "intervals must not be null");
        return calculate(studyDate, StudyTimeCalculationInput.regular(intervals));
    }

    public DailyStudyTime calculate(LocalDate studyDate, StudyTimeCalculationInput input) {
        Objects.requireNonNull(studyDate, "studyDate must not be null");
        Objects.requireNonNull(input, "input must not be null");

        List<StudyInterval> presenceIntervals = mergeIntervals(input.presenceIntervals());
        List<DailyStudyTime.PeriodStudyTime> periodStudyTimes = Arrays.stream(StudyPeriod.values())
                .map(period -> new DailyStudyTime.PeriodStudyTime(
                        period,
                        input.excludedPeriods().contains(period)
                                ? Duration.ZERO
                                : calculateWindowDuration(
                                        studyDate,
                                        period.getStartTime(),
                                        period.getEndTime(),
                                        presenceIntervals
                                )
                ))
                .toList();
        List<DailyStudyTime.BreakStudyTime> breakStudyTimes = Arrays.stream(StudyBreak.values())
                .map(studyBreak -> new DailyStudyTime.BreakStudyTime(
                        studyBreak,
                        studyBreak.isExcludedBy(input.excludedPeriods())
                                ? Duration.ZERO
                                : calculateWindowDuration(
                                        studyDate,
                                        studyBreak.getStartTime(),
                                        studyBreak.getEndTime(),
                                        breakStudyIntervalsFor(
                                                studyBreak,
                                                input.breakStudyIntervals(),
                                                presenceIntervals
                                        )
                                )
                ))
                .toList();

        Duration periodDuration = periodStudyTimes.stream()
                .map(DailyStudyTime.PeriodStudyTime::duration)
                .reduce(Duration.ZERO, Duration::plus);
        Duration breakDuration = breakStudyTimes.stream()
                .map(DailyStudyTime.BreakStudyTime::duration)
                .reduce(Duration.ZERO, Duration::plus);

        return new DailyStudyTime(
                studyDate,
                periodDuration,
                breakDuration,
                periodDuration.plus(breakDuration),
                periodStudyTimes,
                breakStudyTimes
        );
    }

    private List<StudyInterval> breakStudyIntervalsFor(
            StudyBreak studyBreak,
            List<BreakStudyInterval> breakStudyIntervals,
            List<StudyInterval> presenceIntervals
    ) {
        List<StudyInterval> intervalsForBreak = breakStudyIntervals.stream()
                .filter(interval -> interval.studyBreak() == studyBreak)
                .map(BreakStudyInterval::interval)
                .toList();

        return intersectIntervals(presenceIntervals, mergeIntervals(intervalsForBreak));
    }

    private Duration calculateWindowDuration(
            LocalDate studyDate,
            LocalTime startTime,
            LocalTime endTime,
            List<StudyInterval> intervals
    ) {
        Instant windowStart = studyDate.atTime(startTime).atZone(STUDY_ZONE).toInstant();
        Instant windowEnd = studyDate.atTime(endTime).atZone(STUDY_ZONE).toInstant();

        return intervals.stream()
                .map(interval -> overlapDuration(interval, windowStart, windowEnd))
                .reduce(Duration.ZERO, Duration::plus);
    }

    private Duration overlapDuration(StudyInterval interval, Instant windowStart, Instant windowEnd) {
        Instant overlapStart = interval.startedAt().isAfter(windowStart)
                ? interval.startedAt()
                : windowStart;
        Instant overlapEnd = interval.endedAt().isBefore(windowEnd)
                ? interval.endedAt()
                : windowEnd;

        if (!overlapEnd.isAfter(overlapStart)) {
            return Duration.ZERO;
        }

        return Duration.between(overlapStart, overlapEnd);
    }

    private List<StudyInterval> mergeIntervals(List<StudyInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }

        List<StudyInterval> sortedIntervals = intervals.stream()
                .map(interval -> Objects.requireNonNull(interval, "interval must not be null"))
                .sorted(Comparator.comparing(StudyInterval::startedAt))
                .toList();

        List<StudyInterval> mergedIntervals = new ArrayList<>();
        StudyInterval current = sortedIntervals.getFirst();

        for (int index = 1; index < sortedIntervals.size(); index++) {
            StudyInterval next = sortedIntervals.get(index);

            if (next.startedAt().isAfter(current.endedAt())) {
                mergedIntervals.add(current);
                current = next;
                continue;
            }

            Instant mergedEnd = next.endedAt().isAfter(current.endedAt())
                    ? next.endedAt()
                    : current.endedAt();
            current = new StudyInterval(current.startedAt(), mergedEnd);
        }

        mergedIntervals.add(current);
        return List.copyOf(mergedIntervals);
    }

    private List<StudyInterval> intersectIntervals(
            List<StudyInterval> firstIntervals,
            List<StudyInterval> secondIntervals
    ) {
        if (firstIntervals.isEmpty() || secondIntervals.isEmpty()) {
            return List.of();
        }

        List<StudyInterval> intersections = new ArrayList<>();
        int firstIndex = 0;
        int secondIndex = 0;

        while (firstIndex < firstIntervals.size() && secondIndex < secondIntervals.size()) {
            StudyInterval first = firstIntervals.get(firstIndex);
            StudyInterval second = secondIntervals.get(secondIndex);
            Instant intersectionStart = first.startedAt().isAfter(second.startedAt())
                    ? first.startedAt()
                    : second.startedAt();
            Instant intersectionEnd = first.endedAt().isBefore(second.endedAt())
                    ? first.endedAt()
                    : second.endedAt();

            if (intersectionEnd.isAfter(intersectionStart)) {
                intersections.add(new StudyInterval(intersectionStart, intersectionEnd));
            }

            int endComparison = first.endedAt().compareTo(second.endedAt());
            if (endComparison <= 0) {
                firstIndex++;
            }
            if (endComparison >= 0) {
                secondIndex++;
            }
        }

        return List.copyOf(intersections);
    }
}
