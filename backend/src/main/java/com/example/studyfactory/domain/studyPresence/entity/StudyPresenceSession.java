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
        check = {
                @CheckConstraint(
                        name = "ck_study_presence_sessions_state",
                        constraint = """
                                (checked_out_at is null and close_reason is null
                                    and active_member_id is not null and active_member_id = member_id)
                                or (checked_out_at is not null and close_reason is not null and active_member_id is null)
                                """
                ),
                @CheckConstraint(
                        name = "ck_study_presence_sessions_check_in_audit",
                        constraint = """
                                (check_in_method is null
                                    and checked_in_by_member_id is null
                                    and manual_check_in_reason is null)
                                or (check_in_method = 'QR'
                                    and checked_in_by_member_id = member_id
                                    and manual_check_in_reason is null)
                                or (check_in_method = 'MANAGER'
                                    and checked_in_by_member_id is not null
                                    and manual_check_in_reason is not null
                                    and manual_check_in_reason <> '')
                                """
                )
        },
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

    public static final int MANUAL_CHECK_IN_REASON_MAX_LENGTH = 200;

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

    /*
     * Nullable on purpose. deploy.yml runs migrations before swapping the
     * backend container, and restore_previous_backend() can put the pre-audit
     * image back, so rows written without these columns must remain legal.
     * Everything this application writes goes through qrCheckIn/managerCheckIn,
     * which always populate them.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 20, updatable = false)
    private StudyPresenceCheckInMethod checkInMethod;

    /** The member for QR check-in, the ADMIN/STAFF operator for a manual one. */
    @Column(name = "checked_in_by_member_id", updatable = false)
    private Long checkedInByMemberId;

    @Column(name = "manual_check_in_reason", length = MANUAL_CHECK_IN_REASON_MAX_LENGTH, updatable = false)
    private String manualCheckInReason;

    private StudyPresenceSession(
            Long memberId,
            Long branchId,
            Instant checkedInAt,
            StudyPresenceCheckInMethod checkInMethod,
            Long checkedInByMemberId,
            String manualCheckInReason
    ) {
        this.memberId = memberId;
        this.branchId = branchId;
        this.checkedInAt = checkedInAt;
        this.activeMemberId = memberId;
        this.checkInMethod = checkInMethod;
        this.checkedInByMemberId = checkedInByMemberId;
        this.manualCheckInReason = manualCheckInReason;
    }

    /** The member scanned the door QR: they are their own check-in author. */
    public static StudyPresenceSession qrCheckIn(Long memberId, Long branchId, Instant checkedInAt) {
        return new StudyPresenceSession(
                memberId,
                branchId,
                checkedInAt,
                StudyPresenceCheckInMethod.QR,
                memberId,
                null
        );
    }

    /** An ADMIN or STAFF operator recorded the check-in, with a required reason. */
    public static StudyPresenceSession managerCheckIn(
            Long memberId,
            Long branchId,
            Instant checkedInAt,
            Long operatorMemberId,
            String reason
    ) {
        if (operatorMemberId == null) {
            throw new IllegalArgumentException("operatorMemberId must not be null");
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw StudyPresenceException.manualCheckInReasonRequired();
        }
        if (normalizedReason.length() > MANUAL_CHECK_IN_REASON_MAX_LENGTH) {
            throw StudyPresenceException.manualCheckInReasonTooLong();
        }

        return new StudyPresenceSession(
                memberId,
                branchId,
                checkedInAt,
                StudyPresenceCheckInMethod.MANAGER,
                operatorMemberId,
                normalizedReason
        );
    }

    public boolean isManuallyCheckedIn() {
        return checkInMethod == StudyPresenceCheckInMethod.MANAGER;
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
