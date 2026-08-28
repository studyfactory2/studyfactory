package com.example.studyfactory.domain.studyBreak.service;

import com.example.studyfactory.domain.studyBreak.model.StudyBreakWindow;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StudyBreakWindowPolicy {

    public static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");

    public Optional<StudyBreakWindow> findCurrent(Instant now) {
        LocalDate studyDate = now.atZone(STUDY_ZONE).toLocalDate();
        return Arrays.stream(StudyBreak.values())
                .map(studyBreak -> windowFor(studyDate, studyBreak))
                .filter(window -> window.contains(now))
                .findFirst();
    }

    public StudyBreakWindow windowFor(LocalDate studyDate, StudyBreak studyBreak) {
        return new StudyBreakWindow(
                studyDate,
                studyBreak,
                studyDate.atTime(studyBreak.getStartTime()).atZone(STUDY_ZONE).toInstant(),
                studyDate.atTime(studyBreak.getEndTime()).atZone(STUDY_ZONE).toInstant()
        );
    }

    public LocalDate studyDateAt(Instant instant) {
        return instant.atZone(STUDY_ZONE).toLocalDate();
    }
}
