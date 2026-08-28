package com.example.studyfactory.domain.studyTime.repository;

import com.example.studyfactory.domain.studyTime.entity.StudyPresenceSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPresenceSessionRepository extends JpaRepository<StudyPresenceSession, Long> {

    Optional<StudyPresenceSession> findByActiveMemberId(Long memberId);

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
