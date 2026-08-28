package com.example.studyfactory.domain.studyBreak.repository;

import com.example.studyfactory.domain.studyBreak.entity.StudyBreakSession;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow;
import com.example.studyfactory.domain.studyBreak.model.StudyBreakReconciliationCandidate;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyBreakSessionRepository extends JpaRepository<StudyBreakSession, Long> {

    Optional<StudyBreakSession> findByActiveMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyBreakSession session
            where session.activeMemberId = :memberId
            """)
    Optional<StudyBreakSession> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);

    @Query("""
            select new com.example.studyfactory.domain.studyBreak.model.StudyBreakReconciliationCandidate(
                session.id,
                session.presenceSessionId
            )
            from StudyBreakSession session
            where session.activeMemberId is not null
            order by session.windowEndedAt asc, session.id asc
            """)
    List<StudyBreakReconciliationCandidate> findActiveReconciliationCandidates();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyBreakSession session
            where session.id = :sessionId
              and session.activeMemberId is not null
            """)
    Optional<StudyBreakSession> findActiveByIdForUpdate(@Param("sessionId") Long sessionId);

    @Query("""
            select session
            from StudyBreakSession session
            where session.memberId = :memberId
              and session.startedAt < :windowEnd
              and (session.endedAt is null or session.endedAt > :windowStart)
            order by session.startedAt asc, session.id asc
            """)
    List<StudyBreakSession> findOverlappingByMemberId(
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            select new com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow(
                session.id,
                session.presenceSessionId,
                session.memberId,
                session.branchId,
                session.studyDate,
                session.studyBreak,
                session.startedAt,
                session.endedAt,
                session.windowEndedAt
            )
            from StudyBreakSession session
            where session.memberId = :memberId
              and session.studyDate between :fromDate and :toDate
              and session.startedAt < :windowEnd
              and (
                  (session.endedAt is not null and session.endedAt > :windowStart)
                  or (session.endedAt is null and session.windowEndedAt > :windowStart)
              )
            order by session.studyDate asc, session.startedAt asc, session.id asc
            """)
    List<StudyBreakIntervalRow> findIntervalRowsByMemberId(
            @Param("memberId") Long memberId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            select new com.example.studyfactory.domain.studyBreak.model.StudyBreakIntervalRow(
                session.id,
                session.presenceSessionId,
                session.memberId,
                session.branchId,
                session.studyDate,
                session.studyBreak,
                session.startedAt,
                session.endedAt,
                session.windowEndedAt
            )
            from StudyBreakSession session
            where session.branchId = :branchId
              and session.memberId = :memberId
              and session.studyDate between :fromDate and :toDate
              and session.startedAt < :windowEnd
              and (
                  (session.endedAt is not null and session.endedAt > :windowStart)
                  or (session.endedAt is null and session.windowEndedAt > :windowStart)
              )
            order by session.studyDate asc, session.startedAt asc, session.id asc
            """)
    List<StudyBreakIntervalRow> findIntervalRowsByBranchIdAndMemberId(
            @Param("branchId") Long branchId,
            @Param("memberId") Long memberId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );
}
