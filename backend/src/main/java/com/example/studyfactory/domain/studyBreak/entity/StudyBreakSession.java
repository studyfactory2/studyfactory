package com.example.studyfactory.domain.studyBreak.entity;

import com.example.studyfactory.common.BaseEntity;
import com.example.studyfactory.domain.studyTime.model.StudyBreak;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "study_break_sessions",
        check = {
                @CheckConstraint(
                        name = "ck_study_break_sessions_state",
                        constraint = """
                                (ended_at is null and end_reason is null
                                    and active_member_id is not null and active_member_id = member_id)
                                or (ended_at is not null and end_reason is not null and active_member_id is null)
                                """
                ),
                @CheckConstraint(
                        name = "ck_study_break_sessions_time",
                        constraint = """
                                window_started_at < window_ended_at
                                and started_at >= window_started_at
                                and started_at < window_ended_at
                                and (ended_at is null
                                    or (ended_at >= started_at and ended_at <= window_ended_at))
                                """
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_study_break_sessions_active_member",
                columnNames = "active_member_id"
        ),
        indexes = {
                @Index(
                        name = "idx_study_break_sessions_member_date_started",
                        columnList = "member_id, study_date, started_at"
                ),
                @Index(
                        name = "idx_study_break_sessions_branch_date_started",
                        columnList = "branch_id, study_date, started_at"
                ),
                @Index(
                        name = "idx_study_break_sessions_presence_started",
                        columnList = "presence_session_id, started_at"
                ),
                @Index(
                        name = "idx_study_break_sessions_active_window_end",
                        columnList = "active_member_id, window_ended_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyBreakSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "presence_session_id", nullable = false, updatable = false)
    private Long presenceSessionId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "study_date", nullable = false, updatable = false)
    private LocalDate studyDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_break", nullable = false, updatable = false, length = 30)
    private StudyBreak studyBreak;

    @Column(name = "window_started_at", nullable = false, updatable = false)
    private Instant windowStartedAt;

    @Column(name = "window_ended_at", nullable = false, updatable = false)
    private Instant windowEndedAt;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private StudyBreakEndReason endReason;

    @Column(name = "active_member_id")
    private Long activeMemberId;

    public StudyBreakSession(
            Long presenceSessionId,
            Long memberId,
            Long branchId,
            LocalDate studyDate,
            StudyBreak studyBreak,
            Instant windowStartedAt,
            Instant windowEndedAt,
            Instant startedAt
    ) {
        this.presenceSessionId = Objects.requireNonNull(presenceSessionId, "presenceSessionId must not be null");
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        this.studyDate = Objects.requireNonNull(studyDate, "studyDate must not be null");
        this.studyBreak = Objects.requireNonNull(studyBreak, "studyBreak must not be null");
        this.windowStartedAt = Objects.requireNonNull(windowStartedAt, "windowStartedAt must not be null");
        this.windowEndedAt = Objects.requireNonNull(windowEndedAt, "windowEndedAt must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");

        if (!windowEndedAt.isAfter(windowStartedAt)) {
            throw new IllegalArgumentException("Break window end must be after its start");
        }
        if (startedAt.isBefore(windowStartedAt) || !startedAt.isBefore(windowEndedAt)) {
            throw new IllegalArgumentException("Break study must start inside its break window");
        }

        this.activeMemberId = memberId;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public boolean endAtBreakBoundaryIfExpired(Instant asOf) {
        if (!isActive() || asOf.isBefore(windowEndedAt)) {
            return false;
        }
        close(windowEndedAt, StudyBreakEndReason.BREAK_ENDED);
        return true;
    }

    public void stopByMember(Instant stoppedAt) {
        if (!stoppedAt.isBefore(windowEndedAt)) {
            close(windowEndedAt, StudyBreakEndReason.BREAK_ENDED);
            return;
        }
        close(laterOf(stoppedAt, startedAt), StudyBreakEndReason.MEMBER_STOP);
    }

    public void endForPresence(Instant presenceEndedAt) {
        Instant cappedAtBreakEnd = presenceEndedAt.isBefore(windowEndedAt)
                ? presenceEndedAt
                : windowEndedAt;
        Instant effectiveEndedAt = laterOf(cappedAtBreakEnd, startedAt);
        StudyBreakEndReason reason = presenceEndedAt.isBefore(windowEndedAt)
                ? StudyBreakEndReason.PRESENCE_ENDED
                : StudyBreakEndReason.BREAK_ENDED;
        close(effectiveEndedAt, reason);
    }

    private void close(Instant endedAt, StudyBreakEndReason endReason) {
        if (!isActive()) {
            throw new IllegalStateException("Break study session is already closed");
        }
        if (endedAt.isBefore(startedAt) || endedAt.isAfter(windowEndedAt)) {
            throw new IllegalArgumentException("Break study end must stay inside its break window");
        }

        this.endedAt = endedAt;
        this.endReason = Objects.requireNonNull(endReason, "endReason must not be null");
        this.activeMemberId = null;
    }

    private Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
