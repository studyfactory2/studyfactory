package com.example.studyfactory.domain.studyPresence.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyBreak.service.StudyBreakLifecycleService;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceDoorQrResponse;
import com.example.studyfactory.domain.studyPresence.dto.StudyPresenceManagerSessionResponse;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyPresence.qr.StudyPresenceQrTokenProvider;
import com.example.studyfactory.domain.studyPresence.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPresenceService {

    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final MemberRepository memberRepository;
    private final StudyPresenceQrTokenProvider studyPresenceQrTokenProvider;
    private final StudyPresenceAutoClosePolicy autoClosePolicy;
    private final StudyBreakLifecycleService studyBreakLifecycleService;
    private final Clock clock;

    @Transactional
    public StudyPresenceSession checkIn(Long memberId, String qrToken) {
        Long qrBranchId = studyPresenceQrTokenProvider.getBranchId(qrToken);
        Member member = findMemberForUpdate(memberId);
        validateMemberQrBranch(member.getBranchId(), qrBranchId);
        Optional<StudyPresenceSession> activeSession =
                studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId);
        Instant checkedInAt = clock.instant();
        if (activeSession.isPresent()) {
            if (!autoClosePolicy.automaticallyCloseIfStale(activeSession.get(), checkedInAt)) {
                throw StudyPresenceException.alreadyCheckedIn();
            }
            closeBreakStudyForEndedPresence(activeSession.get());
            studyPresenceSessionRepository.flush();
        }

        StudyPresenceSession session = StudyPresenceSession.qrCheckIn(
                member.getId(),
                member.getBranchId(),
                checkedInAt
        );
        return studyPresenceSessionRepository.save(session);
    }

    @Transactional
    public StudyPresenceSession checkOut(Long memberId, String qrToken) {
        Long qrBranchId = studyPresenceQrTokenProvider.getBranchId(qrToken);
        findMemberForUpdate(memberId);
        StudyPresenceSession session = studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId)
                .orElseThrow(StudyPresenceException::notCheckedIn);
        validateSessionQrBranch(session.getBranchId(), qrBranchId);

        Instant checkedOutAt = clock.instant();
        if (!autoClosePolicy.automaticallyCloseIfStale(session, checkedOutAt)) {
            session.checkOut(checkedOutAt);
        }
        closeBreakStudyForEndedPresence(session);
        return session;
    }

    @Transactional(readOnly = true)
    public Optional<StudyPresenceSession> findActive(Long memberId) {
        findMember(memberId);
        Instant now = clock.instant();
        return studyPresenceSessionRepository.findByActiveMemberId(memberId)
                .filter(session -> !autoClosePolicy.shouldAutomaticallyClose(session, now));
    }

    @Transactional(readOnly = true)
    public StudyPresenceDoorQrResponse findDoorQr(Long currentMemberId) {
        Member currentMember = findMember(currentMemberId);
        validateAdmin(currentMember);

        Long branchId = currentMember.getBranchId();
        return new StudyPresenceDoorQrResponse(
                branchId,
                studyPresenceQrTokenProvider.createToken(branchId)
        );
    }

    /**
     * Records a check-in for a member on their behalf. The member row is locked
     * exactly as the QR flow locks it, so a concurrent QR scan and a manual
     * entry cannot both create an active session.
     */
    @Transactional
    public StudyPresenceManagerSessionResponse managerCheckIn(
            Long currentMemberId,
            Long memberId,
            Instant requestedCheckedInAt,
            String reason
    ) {
        Member manager = findMember(currentMemberId);
        validateOperations(manager);

        Member target = findMemberForUpdate(memberId);
        validateSameBranch(manager, target);
        validateManualCheckInTarget(target);

        Optional<StudyPresenceSession> activeSession =
                studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId);

        /*
         * Read the clock only once both pessimistic locks are held. Waiting on a
         * contended member row can take arbitrarily long — across Seoul midnight
         * in the worst case — and a time captured before the wait would validate
         * "today", close stale sessions and stamp the response against a day
         * that has already ended.
         */
        Instant now = clock.instant();
        validateManualCheckInTime(requestedCheckedInAt, now);

        if (activeSession.isPresent()) {
            if (!autoClosePolicy.automaticallyCloseIfStale(activeSession.get(), now)) {
                throw StudyPresenceException.alreadyCheckedIn();
            }
            closeBreakStudyForEndedPresence(activeSession.get());
            studyPresenceSessionRepository.flush();
        }

        validateNoOverlappingSession(memberId, requestedCheckedInAt, now);

        StudyPresenceSession session = studyPresenceSessionRepository.save(
                StudyPresenceSession.managerCheckIn(
                        target.getId(),
                        target.getBranchId(),
                        requestedCheckedInAt,
                        manager.getId(),
                        reason
                )
        );

        return StudyPresenceManagerSessionResponse.from(
                session,
                target,
                session.getCheckedInAt(),
                now,
                now
        );
    }

    @Transactional
    public StudyPresenceManagerSessionResponse managerCheckOut(Long currentMemberId, Long sessionId) {
        Member manager = findMember(currentMemberId);
        validateOperations(manager);
        StudyPresenceSession session = studyPresenceSessionRepository.findByIdAndBranchIdForUpdate(
                        sessionId,
                        manager.getBranchId()
                )
                .orElseThrow(StudyPresenceException::sessionNotFound);

        Instant checkedOutAt = clock.instant();
        if (!autoClosePolicy.automaticallyCloseIfStale(session, checkedOutAt)) {
            session.managerCheckOut(checkedOutAt, currentMemberId);
        }
        closeBreakStudyForEndedPresence(session);
        Member targetMember = memberRepository.findById(session.getMemberId()).orElse(null);

        return StudyPresenceManagerSessionResponse.from(
                session,
                targetMember,
                session.getCheckedInAt(),
                checkedOutAt,
                checkedOutAt
        );
    }

    @Transactional
    public void closeActiveSessionForMemberDeletion(Long memberId) {
        findMemberForUpdate(memberId);
        studyPresenceSessionRepository.findActiveByMemberIdForUpdate(memberId)
                .ifPresent(session -> {
                    Instant checkedOutAt = clock.instant();
                    if (!autoClosePolicy.automaticallyCloseIfStale(session, checkedOutAt)) {
                        session.closeForMemberDeletion(checkedOutAt);
                    }
                    closeBreakStudyForEndedPresence(session);
                });
    }

    private void closeBreakStudyForEndedPresence(StudyPresenceSession session) {
        studyBreakLifecycleService.closeForPresenceEnd(
                session.getMemberId(),
                session.getId(),
                session.getCheckedOutAt()
        );
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(MemberException::memberNotFound);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(MemberException::memberNotFound);
    }

    private void validateAdmin(Member member) {
        if (member.getRole() != MemberRole.ADMIN) {
            throw MemberException.forbidden();
        }
    }

    private void validateOperations(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private void validateSameBranch(Member manager, Member target) {
        if (!manager.getBranchId().equals(target.getBranchId())) {
            throw MemberException.forbidden();
        }
    }

    private void validateManualCheckInTarget(Member target) {
        if (target.getRole() != MemberRole.MEMBER) {
            throw MemberException.forbidden();
        }
    }

    /**
     * The requested instant must be in the past and inside the current Asia/Seoul
     * calendar day. It may sit before the first configured period — that is
     * attendance only, and the study-time calculator still starts recognizing
     * study when the first period opens.
     */
    private void validateManualCheckInTime(Instant requestedCheckedInAt, Instant now) {
        if (requestedCheckedInAt.isAfter(now)) {
            throw StudyPresenceException.futureManualCheckInTime();
        }
        if (requestedCheckedInAt.isBefore(autoClosePolicy.currentSeoulDayStartedAt(now))) {
            throw StudyPresenceException.manualCheckInOutsideCurrentDay();
        }
    }

    /** Rejects a requested interval that runs through any other session for the member. */
    private void validateNoOverlappingSession(Long memberId, Instant windowStart, Instant windowEnd) {
        if (!studyPresenceSessionRepository
                .findOverlappingByMemberId(memberId, windowStart, windowEnd)
                .isEmpty()) {
            throw StudyPresenceException.overlappingSession();
        }
    }

    private void validateMemberQrBranch(Long expectedBranchId, Long qrBranchId) {
        if (!expectedBranchId.equals(qrBranchId)) {
            throw StudyPresenceException.memberQrBranchMismatch();
        }
    }

    private void validateSessionQrBranch(Long expectedBranchId, Long qrBranchId) {
        if (!expectedBranchId.equals(qrBranchId)) {
            throw StudyPresenceException.sessionQrBranchMismatch();
        }
    }
}
