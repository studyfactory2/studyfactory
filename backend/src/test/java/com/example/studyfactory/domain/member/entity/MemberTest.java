package com.example.studyfactory.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원 도메인 테스트")
class MemberTest {

    @Test
    @DisplayName("회원 엔티티를 생성한다")
    void createMember() {
        Member member = new Member(
                1L,
                2L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );

        assertThat(member.getReferenceInformation().getBranchId()).isEqualTo(1L);
        assertThat(member.getReferenceInformation().getEmployeeTypeId()).isEqualTo(2L);
        assertThat(member.getName()).isEqualTo("hong");
        assertThat(member.getPassword()).isEqualTo("password123");
        assertThat(member.getWorkInformation().getSeatNumber()).isEqualTo(12);
        assertThat(member.getWorkInformation().getJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(member.getReferenceInformation().getNameplateContentId()).isEqualTo(3L);
    }
}
