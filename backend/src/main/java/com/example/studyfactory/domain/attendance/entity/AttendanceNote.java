package com.example.studyfactory.domain.attendance.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "attendance_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceNote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private LocalDate noteDate;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    public AttendanceNote(Long branchId, LocalDate noteDate, String content, Long createdByMemberId) {
        this.branchId = branchId;
        this.noteDate = noteDate;
        this.content = content;
        this.createdByMemberId = createdByMemberId;
    }
}
