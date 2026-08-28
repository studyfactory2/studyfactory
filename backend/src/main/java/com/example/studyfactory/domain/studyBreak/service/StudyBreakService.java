package com.example.studyfactory.domain.studyBreak.service;

import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakCommandResponse;
import com.example.studyfactory.domain.studyBreak.dto.StudyBreakStatusResponse;
import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.exception.StudyBreakException;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakWindow;
import com.example.studyfactory.domain.studyBreak.repository.StudyBreakSessionRepository;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import com.example.studyfactory.domain.studyPresence.service.StudyPresenceAutoClosePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyBreakService {

    private final StudyBreakSessionRepository studyBreakSessionRepository;
    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final MemberRepository memberRepository;
    private final StudyBreakWindowPolicy windowPolicy;
    private final StudyPresenceAutoClosePolicy presenceAutoClosePolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StudyBreakStatusResponse findStatus(Long memberId) {
        findMember(memberId);
        Instant asOf = clock.instant();
        Optional<StudyPresenceSession> presenceSession = logicalPresence(
                studyPresenceSessionRepository.findByActiveMemberId(memberId),
                asOf
        );
        Optional<StudyBreakSession> activeSession = logicalActiveBreak(
                studyBreakSessionRepository.findByActiveMemberId(memberId),
                presenceSession,
                asOf
        );

        return statusAt(asOf, presenceSession, activeSession);
    }

    @Transactional
    public StudyBreakCommandResponse start(Long memberId) {
        lockMember(memberId);
        Optional<StudyPresenceSession> storedPresence =
                studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId);
        Optional<StudyBreakSession> storedActiveBreak =
                studyBreakSessionRepository.findActiveByMemberIdForUpdate(memberId);
        Instant now = clock.instant();
        Optional<StudyPresenceSession> presenceSession = logicalPresence(storedPresence, now);
        if (presenceSession.isEmpty()) {
            throw StudyBreakException.presenceRequired();
        }
        StudyBreakWindow currentWindow = windowPolicy.findCurrent(now)
                .orElseThrow(StudyBreakException::outsideBreak);

        Optional<StudyBreakSession> activeSession = reconcileForCommand(
                storedActiveBreak,
                presenceSession,
                now
        );
        if (activeSession.isPresent()) {
            StudyBreakSession session = activeSession.get();
            if (session.getPresenceSessionId().equals(presenceSession.get().getId())
                    && session.getStudyBreak() == currentWindow.studyBreak()) {
                return new StudyBreakCommandResponse(
                        false,
                        statusAt(now, presenceSession, activeSession)
                );
            }
            throw StudyBreakException.conflictingActiveSession();
        }

        if (storedActiveBreak.isPresent()) {
            studyBreakSessionRepository.flush();
        }
        StudyPresenceSession presence = presenceSession.get();
        StudyBreakSession createdSession = studyBreakSessionRepository.save(new StudyBreakSession(
                presence.getId(),
                memberId,
                presence.getBranchId(),
                currentWindow.studyDate(),
                currentWindow.studyBreak(),
                currentWindow.startedAt(),
                currentWindow.endedAt(),
                now
        ));

        return new StudyBreakCommandResponse(
                true,
                statusAt(now, presenceSession, Optional.of(createdSession))
        );
    }

    @Transactional
    public StudyBreakCommandResponse stop(Long memberId) {
        lockMember(memberId);
        Optional<StudyPresenceSession> storedPresence =
                studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId);
        Optional<StudyBreakSession> storedActiveBreak =
                studyBreakSessionRepository.findActiveByMemberIdForUpdate(memberId);
        Instant now = clock.instant();
        Optional<StudyPresenceSession> presenceSession = logicalPresence(storedPresence, now);
        Optional<StudyBreakSession> activeSession = reconcileForCommand(
                storedActiveBreak,
                presenceSession,
                now
        );
        boolean changed = false;
        if (activeSession.isPresent()) {
            activeSession.get().stopByMember(now);
            changed = true;
        }

        return new StudyBreakCommandResponse(
                changed,
                statusAt(now, presenceSession, Optional.empty())
        );
    }

    private Optional<StudyBreakSession> reconcileForCommand(
            Optional<StudyBreakSession> storedActiveBreak,
            Optional<StudyPresenceSession> presenceSession,
            Instant now
    ) {
        if (storedActiveBreak.isEmpty()) {
            return Optional.empty();
        }

        StudyBreakSession activeSession = storedActiveBreak.get();
        if (presenceSession.isEmpty()
                || !activeSession.getPresenceSessionId().equals(presenceSession.get().getId())) {
            activeSession.endForPresence(logicalPresenceEnd(storedActiveBreak.get(), now));
            return Optional.empty();
        }
        if (activeSession.endAtBreakBoundaryIfExpired(now)) {
            return Optional.empty();
        }
        return Optional.of(activeSession);
    }

    private Instant logicalPresenceEnd(StudyBreakSession breakSession, Instant now) {
        return studyPresenceSessionRepository.findById(breakSession.getPresenceSessionId())
                .map(StudyPresenceSession::getCheckedOutAt)
                .filter(endedAt -> endedAt != null)
                .orElse(now);
    }

    private Optional<StudyPresenceSession> logicalPresence(
            Optional<StudyPresenceSession> storedPresence,
            Instant asOf
    ) {
        return storedPresence.filter(session -> !presenceAutoClosePolicy.shouldAutomaticallyClose(session, asOf));
    }

    private Optional<StudyBreakSession> logicalActiveBreak(
            Optional<StudyBreakSession> storedActiveBreak,
            Optional<StudyPresenceSession> presenceSession,
            Instant asOf
    ) {
        return storedActiveBreak
                .filter(session -> asOf.isBefore(session.getWindowEndedAt()))
                .filter(session -> presenceSession
                        .map(presence -> session.getPresenceSessionId().equals(presence.getId()))
                        .orElse(false));
    }

    private StudyBreakStatusResponse statusAt(
            Instant asOf,
            Optional<StudyPresenceSession> presenceSession,
            Optional<StudyBreakSession> activeSession
    ) {
        return StudyBreakStatusResponse.from(
                asOf,
                windowPolicy.studyDateAt(asOf),
                presenceSession.isPresent(),
                windowPolicy.findCurrent(asOf),
                activeSession
        );
    }

    private void findMember(Long memberId) {
        memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void lockMember(Long memberId) {
        memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberException::memberNotFound);
    }
}
