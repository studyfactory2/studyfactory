package com.example.studyfactory.domain.attendance.repository;

import com.example.studyfactory.domain.attendance.entity.AttendanceNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceNoteRepository extends JpaRepository<AttendanceNote, Long> {
}
