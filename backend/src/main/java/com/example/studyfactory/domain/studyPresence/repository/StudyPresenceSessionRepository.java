package com.example.studyfactory.domain.studyPresence.repository;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPresenceSessionRepository extends JpaRepository<StudyPresenceSession, Long> {

    Optional<StudyPresenceSession> findByActiveMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyPresenceSession session
            where session.id = :sessionId
            """)
    Optional<StudyPresenceSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyPresenceSession session
            where session.id = :sessionId
              and session.branchId = :branchId
            """)
    Optional<StudyPresenceSession> findByIdAndBranchIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("branchId") Long branchId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyPresenceSession session
            where session.activeMemberId = :memberId
            """)
    Optional<StudyPresenceSession> findActiveByMemberIdForUpdate(@Param("memberId") Long memberId);

    @Query("""
            select session
            from StudyPresenceSession session
            where session.branchId = :branchId
              and session.activeMemberId is not null
            order by session.checkedInAt asc, session.id asc
            """)
    List<StudyPresenceSession> findActiveByBranchId(@Param("branchId") Long branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session
            from StudyPresenceSession session
            where session.activeMemberId is not null
              and session.checkedInAt < :staleBefore
            order by session.checkedInAt asc, session.id asc
            """)
    List<StudyPresenceSession> findStaleActiveSessionsForUpdate(@Param("staleBefore") Instant staleBefore);

    @Query("""
            select session
            from StudyPresenceSession session
            where session.branchId = :branchId
              and session.checkedInAt < :windowEnd
              and (session.checkedOutAt is null or session.checkedOutAt > :windowStart)
            order by session.checkedInAt asc, session.id asc
            """)
    List<StudyPresenceSession> findOverlappingByBranchId(
            @Param("branchId") Long branchId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            select session
            from StudyPresenceSession session
            where session.branchId = :branchId
              and session.memberId = :memberId
              and session.checkedInAt < :windowEnd
              and (session.checkedOutAt is null or session.checkedOutAt > :windowStart)
            order by session.checkedInAt asc, session.id asc
            """)
    List<StudyPresenceSession> findOverlappingByBranchIdAndMemberId(
            @Param("branchId") Long branchId,
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("""
            select session
            from StudyPresenceSession session
            where session.memberId = :memberId
              and session.checkedInAt < :windowEnd
              and (session.checkedOutAt is null or session.checkedOutAt > :windowStart)
            order by session.checkedInAt asc, session.id asc
            """)
    List<StudyPresenceSession> findOverlappingByMemberId(
            @Param("memberId") Long memberId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

}
