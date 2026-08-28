package com.example.studyfactory.domain.studyTime.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.studyTime.entity.StudyPresenceSession;
import com.example.studyfactory.domain.studyTime.exception.StudyPresenceException;
import com.example.studyfactory.domain.studyTime.repository.StudyPresenceSessionRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyPresenceService {

    private final StudyPresenceSessionRepository studyPresenceSessionRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public StudyPresenceSession checkIn(Long memberId) {
        Member member = findMemberForUpdate(memberId);
        if (studyPresenceSessionRepository.findByActiveMemberId(memberId).isPresent()) {
            throw StudyPresenceException.alreadyCheckedIn();
        }

        Instant checkedInAt = clock.instant();
        StudyPresenceSession session = new StudyPresenceSession(
                member.getId(),
                member.getBranchId(),
                checkedInAt
        );
        return studyPresenceSessionRepository.save(session);
    }

    @Transactional
    public StudyPresenceSession checkOut(Long memberId) {
        findMemberForUpdate(memberId);
        StudyPresenceSession session = studyPresenceSessionRepository.findByActiveMemberId(memberId)
                .orElseThrow(StudyPresenceException::notCheckedIn);

        session.checkOut(clock.instant());
        return session;
    }

    @Transactional
    public void closeActiveSessionForMemberDeletion(Long memberId) {
        findMemberForUpdate(memberId);
        studyPresenceSessionRepository.findByActiveMemberId(memberId)
                .ifPresent(session -> session.closeForMemberDeletion(clock.instant()));
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(MemberException::memberNotFound);
    }
}
