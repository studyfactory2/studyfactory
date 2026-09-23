package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RegistrationCodeService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int CODE_BOUND = 100_000_000;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    private final byte[] secret;
    private final long expirationHours;

    public RegistrationCodeService(
            @Value("${jwt.secret}") String secret,
            @Value("${registration.code.expiration-hours:24}") long expirationHours
    ) {
        if (secret.isBlank()) {
            throw new IllegalArgumentException("등록 코드 비밀키는 비어 있을 수 없습니다.");
        }
        if (expirationHours <= 0) {
            throw new IllegalArgumentException("등록 코드 만료 시간은 양수여야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationHours = expirationHours;
    }

    public IssuedCode issue(Member member) {
        String code = "%08d".formatted(secureRandom.nextInt(CODE_BOUND));
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusHours(expirationHours);
        member.issueRegistrationCode(hash(member, code), expiresAt);
        return new IssuedCode(code, expiresAt);
    }

    public boolean validate(Member member, String code) {
        if (!requiresCode(member.getRole())) {
            return true;
        }
        if (code == null
                || member.getRegistrationCodeHash() == null
                || member.getRegistrationCodeExpiresAt() == null
                || !member.getRegistrationCodeExpiresAt().isAfter(LocalDateTime.now(clock))
                || member.getRegistrationCodeFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return false;
        }

        boolean valid = MessageDigest.isEqual(
                member.getRegistrationCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(member, code).getBytes(StandardCharsets.US_ASCII)
        );
        if (!valid) {
            member.recordRegistrationCodeFailure();
        }
        return valid;
    }

    public boolean requiresCode(MemberRole role) {
        return role == MemberRole.STAFF || role == MemberRole.ADMIN;
    }

    private String hash(Member member, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            String value = "registration-code|%d|%d|%s|%s|%s".formatted(
                    member.getId(),
                    member.getBranchId(),
                    member.getName(),
                    member.getRole(),
                    code
            );
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("등록 코드 해시를 생성할 수 없습니다.", exception);
        }
    }

    public record IssuedCode(String value, LocalDateTime expiresAt) {
    }
}
