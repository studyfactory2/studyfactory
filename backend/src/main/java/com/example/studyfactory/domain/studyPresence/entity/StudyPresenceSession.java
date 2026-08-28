package com.example.studyfactory.domain.studyPresence.entity;

import com.example.studyfactory.common.BaseEntity;
import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "study_presence_sessions",
        check = @CheckConstraint(
                name = "ck_study_presence_sessions_state",
                constraint = """
                        (checked_out_at is null and close_reason is null
                            and active_member_id is not null and active_member_id = member_id)
                        or (checked_out_at is not null and close_reason is not null and active_member_id is null)
                        """
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_study_presence_sessions_active_member",
                columnNames = "active_member_id"
        ),
        indexes = {
                @Index(
                        name = "idx_study_presence_sessions_member_checked_in",
                        columnList = "member_id, checked_in_at"
                ),
                @Index(
                        name = "idx_study_presence_sessions_branch_checked_in",
                        columnList = "branch_id, checked_in_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyPresenceSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 30)
    private StudyPresenceCloseReason closeReason;

    @Column(name = "active_member_id")
    private Long activeMemberId;

    @Column(name = "closed_by_member_id")
    private Long closedByMemberId;

    @Column(name = "automatically_closed")
    private Boolean automaticallyClosed;

    public StudyPresenceSession(Long memberId, Long branchId, Instant checkedInAt) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.checkedInAt = checkedInAt;
        this.activeMemberId = memberId;
    }

    public boolean isActive() {
        return checkedOutAt == null;
    }

    public boolean isAutomaticallyClosed() {
        return Boolean.TRUE.equals(automaticallyClosed);
    }

    public void checkOut(Instant checkedOutAt) {
        close(checkedOutAt, StudyPresenceCloseReason.CHECK_OUT);
    }

    public void managerCheckOut(Instant checkedOutAt, Long managerMemberId) {
        if (managerMemberId == null) {
            throw new IllegalArgumentException("managerMemberId must not be null");
        }
        close(checkedOutAt, StudyPresenceCloseReason.CHECK_OUT);
        this.closedByMemberId = managerMemberId;
    }

    public void automaticallyCheckOut(Instant checkedOutAt) {
        close(checkedOutAt, StudyPresenceCloseReason.CHECK_OUT);
        this.automaticallyClosed = true;
    }

    public void closeForMemberDeletion(Instant checkedOutAt) {
        close(checkedOutAt, StudyPresenceCloseReason.MEMBER_DELETED);
    }

    private void close(Instant checkedOutAt, StudyPresenceCloseReason closeReason) {
        if (!isActive()) {
            throw StudyPresenceException.alreadyCheckedOut();
        }
        if (checkedOutAt.isBefore(checkedInAt)) {
            throw StudyPresenceException.invalidCheckoutTime();
        }

        this.checkedOutAt = checkedOutAt;
        this.closeReason = closeReason;
        this.activeMemberId = null;
    }
}
