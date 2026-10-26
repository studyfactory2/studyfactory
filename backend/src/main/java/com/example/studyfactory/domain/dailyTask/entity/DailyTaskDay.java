package com.example.studyfactory.domain.dailyTask.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "member_task_days",
        check = @CheckConstraint(name = "ck_member_task_days_revision", constraint = "revision >= 0"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_task_days_member_date",
                columnNames = {"member_id", "task_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyTaskDay extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "task_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private long revision;

    public DailyTaskDay(Long memberId, LocalDate date) {
        this.memberId = memberId;
        this.date = date;
    }

    public void advance() {
        revision = Math.incrementExact(revision);
    }
}
