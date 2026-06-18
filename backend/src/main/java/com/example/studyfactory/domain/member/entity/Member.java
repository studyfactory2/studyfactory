package com.example.studyfactory.domain.member.entity;

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
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long employeeTypeId;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private LocalDate joinDate;

    @Column(nullable = false)
    private Long nameplateContentId;

    @Column(columnDefinition = "text")
    private String drinkSetting;

    @Column(columnDefinition = "text")
    private String drinkNote;

    @Column(columnDefinition = "text")
    private String memberNote;

    public Member(
            Long branchId,
            Long employeeTypeId,
            String name,
            int seatNumber,
            LocalDate joinDate,
            Long nameplateContentId,
            String drinkSetting,
            String drinkNote,
            String memberNote
    ) {
        this.branchId = branchId;
        this.employeeTypeId = employeeTypeId;
        this.name = name;
        this.seatNumber = seatNumber;
        this.joinDate = joinDate;
        this.nameplateContentId = nameplateContentId;
        this.drinkSetting = drinkSetting;
        this.drinkNote = drinkNote;
        this.memberNote = memberNote;
    }
}
