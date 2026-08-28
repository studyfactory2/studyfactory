package com.example.studyfactory.domain.studyPresence.service;

import com.example.studyfactory.domain.studyPresence.entity.StudyPresenceSession;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StudyPresenceAutoClosePolicy {

    private static final ZoneId PRESENCE_ZONE = ZoneId.of("Asia/Seoul");

    private final boolean enabled;

    public StudyPresenceAutoClosePolicy(
            @Value("${study-presence.auto-close.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    public Instant currentSeoulDayStartedAt(Instant now) {
        return now.atZone(PRESENCE_ZONE)
                .toLocalDate()
                .atStartOfDay(PRESENCE_ZONE)
                .toInstant();
    }

    public Instant firstMidnightAfter(Instant checkedInAt) {
        return checkedInAt.atZone(PRESENCE_ZONE)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(PRESENCE_ZONE)
                .toInstant();
    }

    public boolean shouldAutomaticallyClose(StudyPresenceSession session, Instant now) {
        return enabled
                && session.isActive()
                && !now.isBefore(firstMidnightAfter(session.getCheckedInAt()));
    }

    public boolean automaticallyCloseIfStale(StudyPresenceSession session, Instant now) {
        if (!shouldAutomaticallyClose(session, now)) {
            return false;
        }

        session.automaticallyCheckOut(firstMidnightAfter(session.getCheckedInAt()));
        return true;
    }
}
