package com.example.studyfactory.domain.attendance.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "attendance_status_types")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceStatusType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean customAllowed;

    public AttendanceStatusType(String name, boolean customAllowed) {
        this.name = name;
        this.customAllowed = customAllowed;
    }
}
