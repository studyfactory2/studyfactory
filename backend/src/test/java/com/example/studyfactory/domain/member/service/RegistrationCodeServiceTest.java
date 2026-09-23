package com.example.studyfactory.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("등록 코드 서비스 테스트")
class RegistrationCodeServiceTest {

    private final RegistrationCodeService registrationCodeService =
            new RegistrationCodeService("registration-code-test-secret", 24);

    @Test
    @DisplayName("8자리 코드를 발급하고 원문 대신 해시와 만료 시간을 저장한다")
    void issueAndValidateCode() {
        Member staff = pendingMember(MemberRole.STAFF);

        RegistrationCodeService.IssuedCode issuedCode = registrationCodeService.issue(staff);

        assertThat(issuedCode.value()).matches("\\d{8}");
        assertThat(staff.getRegistrationCodeHash())
                .isNotBlank()
                .isNotEqualTo(issuedCode.value());
        assertThat(staff.getRegistrationCodeExpiresAt()).isEqualTo(issuedCode.expiresAt());
        assertThat(staff.getRegistrationCodeFailedAttempts()).isZero();
        assertThat(registrationCodeService.validate(staff, issuedCode.value())).isTrue();
    }

    @Test
    @DisplayName("등록 코드는 다섯 번 실패하면 올바른 코드도 거부한다")
    void lockAfterFiveFailures() {
        Member admin = pendingMember(MemberRole.ADMIN);
        RegistrationCodeService.IssuedCode issuedCode = registrationCodeService.issue(admin);
        String wrongCode = issuedCode.value().equals("00000000") ? "00000001" : "00000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(registrationCodeService.validate(admin, wrongCode)).isFalse();
        }

        assertThat(admin.getRegistrationCodeFailedAttempts()).isEqualTo(5);
        assertThat(registrationCodeService.validate(admin, issuedCode.value())).isFalse();
    }

    @Test
    @DisplayName("만료된 등록 코드는 거부한다")
    void rejectExpiredCode() {
        Member staff = pendingMember(MemberRole.STAFF);
        RegistrationCodeService.IssuedCode issuedCode = registrationCodeService.issue(staff);
        ReflectionTestUtils.setField(
                staff,
                "registrationCodeExpiresAt",
                LocalDateTime.now(Clock.systemUTC()).minusSeconds(1)
        );

        assertThat(registrationCodeService.validate(staff, issuedCode.value())).isFalse();
    }

    @Test
    @DisplayName("가입 완료 시 등록 코드 상태를 모두 제거한다")
    void consumeCodeOnSignup() {
        Member staff = pendingMember(MemberRole.STAFF);
        registrationCodeService.issue(staff);
        staff.recordRegistrationCodeFailure();

        staff.signup("4827");

        assertThat(staff.getPassword()).isEqualTo("4827");
        assertThat(staff.getRegistrationCodeHash()).isNull();
        assertThat(staff.getRegistrationCodeExpiresAt()).isNull();
        assertThat(staff.getRegistrationCodeFailedAttempts()).isZero();
    }

    private Member pendingMember(MemberRole role) {
        Member member = new Member(
                1L,
                "등록 대상",
                null,
                role,
                null,
                LocalDate.of(2026, 9, 23),
                null
        );
        ReflectionTestUtils.setField(member, "id", 10L);
        return member;
    }
}
