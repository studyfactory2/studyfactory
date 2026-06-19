package com.example.studyfactory.domain.room.repository;

import com.example.studyfactory.domain.room.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
