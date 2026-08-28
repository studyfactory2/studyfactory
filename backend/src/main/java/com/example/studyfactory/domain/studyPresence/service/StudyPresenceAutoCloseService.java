package com.example.studyfactory.domain.studyPresence.service;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPresenceAutoCloseService {

    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final StudyPresenceAutoClosePolicy autoClosePolicy;
    private final Clock clock;

    @Transactional
    public int closeStaleSessions() {
        Instant now = clock.instant();
        Instant currentDayStartedAt = autoClosePolicy.currentSeoulDayStartedAt(now);
        List<StudyPresenceSession> staleSessions =
                studyPresenceSessionRepository.findStaleActiveSessionsForUpdate(currentDayStartedAt);

        int closedSessionCount = 0;
        for (StudyPresenceSession session : staleSessions) {
            if (autoClosePolicy.automaticallyCloseIfStale(session, now)) {
                closedSessionCount++;
            }
        }
        return closedSessionCount;
    }
}
