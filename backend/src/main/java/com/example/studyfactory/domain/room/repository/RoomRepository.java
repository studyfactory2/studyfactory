package com.example.studyfactory.domain.room.repository;

import com.example.studyfactory.domain.room.entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByBranchIdOrderByIdAsc(Long branchId);

    Optional<Room> findByBranchIdAndName(Long branchId, String name);
}
