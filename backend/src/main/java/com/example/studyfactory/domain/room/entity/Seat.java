package com.example.studyfactory.domain.room.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"branch_id", "number"}),
                @UniqueConstraint(columnNames = {"room_id", "grid_row", "grid_col"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private int number;

    @Column(name = "member_id", unique = true)
    private Long memberId;

    @Column(nullable = false)
    private int gridRow;

    @Column(nullable = false)
    private int gridCol;

    public Seat(Long branchId, Long roomId, int number, Long memberId, int gridRow, int gridCol) {
        this.branchId = branchId;
        this.roomId = roomId;
        this.number = number;
        this.memberId = memberId;
        this.gridRow = gridRow;
        this.gridCol = gridCol;
    }
}
