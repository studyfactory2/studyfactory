package com.example.studyfactory.domain.studyBreak.service;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakReconciliationCandidate;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyBreakReconciliationService {

    private final StudyBreakSessionRepository studyBreakSessionRepository;
    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final StudyPresenceAutoClosePolicy presenceAutoClosePolicy;
    private final Clock clock;

    @Transactional
    public int reconcileActiveSessions() {
        Instant now = clock.instant();
        int closedSessionCount = 0;
        for (StudyBreakReconciliationCandidate candidate
                : studyBreakSessionRepository.findActiveReconciliationCandidates()) {
            StudyPresenceSession presenceSession = studyPresenceSessionRepository
                    .findByIdForUpdate(candidate.presenceSessionId())
                    .orElse(null);
            StudyBreakSession breakSession = studyBreakSessionRepository
                    .findActiveByIdForUpdate(candidate.breakSessionId())
                    .orElse(null);
            if (breakSession == null) {
                continue;
            }
            if (presenceSession == null) {
                breakSession.endForPresence(now);
                closedSessionCount++;
                continue;
            }
            if (!presenceSession.isActive()) {
                breakSession.endForPresence(presenceSession.getCheckedOutAt());
                closedSessionCount++;
                continue;
            }
            if (presenceAutoClosePolicy.shouldAutomaticallyClose(presenceSession, now)) {
                breakSession.endForPresence(
                        presenceAutoClosePolicy.firstMidnightAfter(presenceSession.getCheckedInAt())
                );
                closedSessionCount++;
                continue;
            }
            if (breakSession.endAtBreakBoundaryIfExpired(now)) {
                closedSessionCount++;
            }
        }
        return closedSessionCount;
    }
}
