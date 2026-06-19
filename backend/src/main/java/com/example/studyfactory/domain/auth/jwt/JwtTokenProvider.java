package com.example.studyfactory.domain.auth.jwt;
import com.example.studyfactory.domain.auth.exception.AuthException;
import com.example.studyfactory.domain.member.entity.Member;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration-millis}")
    private long accessExpirationMillis;

    @Value("${jwt.refresh-expiration-millis}")
    private long refreshExpirationMillis;

    public String createAccessToken(Member member) {
        return createToken(member, accessExpirationMillis);
    }

    public String createRefreshToken(Member member) {
        return createToken(member, refreshExpirationMillis);
    }

    private String createToken(Member member, long expirationMillis) {
        long now = clock.millis();
        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", member.getId());
        payload.put("name", member.getName());
        payload.put("branchId", member.getBranchId());
        payload.put("iat", now / 1000);
        payload.put("exp", (now + expirationMillis) / 1000);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String unsignedToken = encodedHeader + "." + encodedPayload;

        return unsignedToken + "." + sign(unsignedToken);
    }

    public Long getMemberId(String token) {
        Map<String, Object> payload = parseAndValidate(token);
        Object subject = payload.get("sub");
        if (subject instanceof Number number) {
            return number.longValue();
        }
        throw AuthException.invalidToken();
    }

    public void validateToken(String token) {
        parseAndValidate(token);
    }

    private Map<String, Object> parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw AuthException.invalidToken();
            }

            String unsignedToken = parts[0] + "." + parts[1];
            if (!sign(unsignedToken).equals(parts[2])) {
                throw AuthException.invalidToken();
            }

            Map<String, Object> payload = objectMapper.readValue(
                    base64UrlDecode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            validateExpiration(payload);

            return payload;
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AuthException.invalidToken();
        }
    }

    private void validateExpiration(Map<String, Object> payload) {
        Object expiration = payload.get("exp");
        if (!(expiration instanceof Number number) || number.longValue() < clock.millis() / 1000) {
            throw AuthException.invalidToken();
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return base64UrlEncode(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw AuthException.invalidToken();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return base64UrlEncode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw AuthException.invalidToken();
        }
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
