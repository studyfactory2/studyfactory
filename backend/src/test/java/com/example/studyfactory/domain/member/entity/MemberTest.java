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
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );

        assertThat(member.getBranchId()).isEqualTo(1L);
        assertThat(member.getName()).isEqualTo("hong");
        assertThat(member.getPassword()).isEqualTo("password123");
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getSeatNumber()).isEqualTo(12);
        assertThat(member.getJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(member.getNameplateContentId()).isEqualTo(3L);
        assertThat(member.getDrinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(member.getDrinkNote()).isEqualTo("연하게");
        assertThat(member.getMemberNote()).isEqualTo("오전 교육 예정");
    }

    @Test
    @DisplayName("음료 설정과 음료 참고사항을 수정한다")
    void updateDrink() {
        Member member = new Member(
                1L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );

        member.updateDrink("따뜻한 라떼", "시럽 추가");

        assertThat(member.getDrinkSetting()).isEqualTo("따뜻한 라떼");
        assertThat(member.getDrinkNote()).isEqualTo("시럽 추가");
        assertThat(member.getMemberNote()).isEqualTo("오전 교육 예정");
    }

    @Test
    @DisplayName("음료 설정과 음료 참고사항을 삭제한다")
    void deleteDrink() {
        Member member = new Member(
                1L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );

        member.deleteDrink();

        assertThat(member.getDrinkSetting()).isNull();
        assertThat(member.getDrinkNote()).isNull();
        assertThat(member.getMemberNote()).isEqualTo("오전 교육 예정");
    }
}
