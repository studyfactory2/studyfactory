package com.example.studyfactory.domain.room.repository;

import com.example.studyfactory.domain.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}
