package com.example.studyfactory.domain.room.repository;

import com.example.studyfactory.domain.room.entity.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByRoomIdOrderByGridRowAscGridColAsc(Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Seat s where s.roomId = :roomId")
    void deleteByRoomId(Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Seat s where s.branchId = :branchId")
    void deleteByBranchId(@Param("branchId") Long branchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Seat s set s.memberId = null where s.memberId = :memberId")
    void clearMemberAssignment(@Param("memberId") Long memberId);
}
