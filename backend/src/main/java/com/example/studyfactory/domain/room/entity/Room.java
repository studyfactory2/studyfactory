package com.example.studyfactory.domain.room.entity;

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
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int rows;

    @Column(nullable = false)
    private int cols;

    public Room(Long branchId, String name, int rows, int cols) {
        this.branchId = branchId;
        this.name = name;
        this.rows = rows;
        this.cols = cols;
    }

    public void updateLayout(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }
}
