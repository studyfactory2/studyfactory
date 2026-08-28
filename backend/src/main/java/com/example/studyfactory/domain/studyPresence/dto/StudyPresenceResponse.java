package com.example.studyfactory.domain.studyPresence.dto;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceCloseReason;
import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.time.Instant;

public record StudyPresenceResponse(
        Long sessionId,
        Long branchId,
        Instant checkedInAt,
        Instant checkedOutAt,
        StudyPresenceCloseReason closeReason,
        boolean active
) {

    public static StudyPresenceResponse from(StudyPresenceSession session) {
        return new StudyPresenceResponse(
                session.getId(),
                session.getBranchId(),
                session.getCheckedInAt(),
                session.getCheckedOutAt(),
                session.getCloseReason(),
                session.isActive()
        );
    }
}
