package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.studyTime.model.DailyStudyTime;
import com.example.studyfactory.domain.studyTime.model.StudyInterval;
import com.example.studyfactory.domain.studyTime.model.StudyPeriod;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
        Objects.requireNonNull(studyDate, "studyDate must not be null");
        Objects.requireNonNull(intervals, "intervals must not be null");

        List<StudyInterval> mergedIntervals = mergeIntervals(intervals);
        List<DailyStudyTime.PeriodStudyTime> periodStudyTimes = Arrays.stream(StudyPeriod.values())
                .map(period -> new DailyStudyTime.PeriodStudyTime(
                        period,
                        calculatePeriodDuration(studyDate, period, mergedIntervals)
                ))
                .toList();

        Duration totalDuration = periodStudyTimes.stream()
                .map(DailyStudyTime.PeriodStudyTime::duration)
                .reduce(Duration.ZERO, Duration::plus);

        return new DailyStudyTime(studyDate, totalDuration, periodStudyTimes);
    }

    private Duration calculatePeriodDuration(
            LocalDate studyDate,
            StudyPeriod period,
            List<StudyInterval> intervals
    ) {
        Instant periodStart = studyDate.atTime(period.getStartTime()).atZone(STUDY_ZONE).toInstant();
        Instant periodEnd = studyDate.atTime(period.getEndTime()).atZone(STUDY_ZONE).toInstant();

        return intervals.stream()
                .map(interval -> overlapDuration(interval, periodStart, periodEnd))
                .reduce(Duration.ZERO, Duration::plus);
    }

    private Duration overlapDuration(StudyInterval interval, Instant periodStart, Instant periodEnd) {
        Instant overlapStart = interval.startedAt().isAfter(periodStart)
                ? interval.startedAt()
                : periodStart;
        Instant overlapEnd = interval.endedAt().isBefore(periodEnd)
                ? interval.endedAt()
                : periodEnd;

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
}
