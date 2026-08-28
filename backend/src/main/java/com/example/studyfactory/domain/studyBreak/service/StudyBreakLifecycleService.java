package com.example.studyfactory.domain.studyBreak.service;

import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyBreakLifecycleService {

    private final StudyBreakSessionRepository studyBreakSessionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void closeForPresenceEnd(
            Long memberId,
            Long presenceSessionId,
            Instant presenceEndedAt
    ) {
        studyBreakSessionRepository.findActiveByMemberIdForUpdate(memberId)
                .filter(session -> session.getPresenceSessionId().equals(presenceSessionId))
                .ifPresent(session -> session.endForPresence(presenceEndedAt));
    }
}
