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
                null,
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );

        assertThat(member.getBranchId()).isEqualTo(1L);
        assertThat(member.getName()).isEqualTo("hong");
        assertThat(member.getPassword()).isNull();
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getSeatNumber()).isEqualTo(12);
        assertThat(member.getJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(member.getCertificationId()).isEqualTo(3L);
        assertThat(member.getMemberNote()).isEqualTo("오전 교육 예정");
    }

    @Test
    @DisplayName("비밀번호를 세팅해 회원가입을 완료한다")
    void signup() {
        Member member = new Member(1L, "hong", null, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");

        member.signup("password123");

        assertThat(member.getPassword()).isEqualTo("password123");
    }

    @Test
    @DisplayName("관리자와 스태프는 전체 권한을 가진다")
    void adminAndStaffHaveAllPermissions() {
        assertThat(MemberRole.ADMIN.hasAllPermissions()).isTrue();
        assertThat(MemberRole.STAFF.hasAllPermissions()).isTrue();
        assertThat(MemberRole.MEMBER.hasAllPermissions()).isFalse();
    }
}
