package com.example.studyfactory.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.auth.exception.AuthException;
import com.example.studyfactory.domain.member.entity.Member;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JWT 토큰 제공자 테스트")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(new ObjectMapper());
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", "test-secret-key");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpirationMillis", 3600000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMillis", 1209600000L);
    }

    @Test
    @DisplayName("회원 정보로 JWT 토큰을 발급하고 회원 ID를 추출한다")
    void createTokenAndGetMemberId() {
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);

        String token = jwtTokenProvider.createAccessToken(member);

        assertThat(token).contains(".");
        assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        assertThat(decodePayload(token).get("role")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("회원 정보로 액세스 토큰과 리프레시 토큰을 각각 발급한다")
    void createAccessTokenAndRefreshToken() {
        Member member = createMember();
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);

        assertThat(accessToken).contains(".");
        assertThat(refreshToken).contains(".");
        assertThat(jwtTokenProvider.getMemberId(accessToken)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getMemberId(refreshToken)).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효하지 않은 JWT 토큰이면 예외가 발생한다")
    void throwExceptionWhenTokenIsInvalid() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken("invalid-token"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("유효하지 않은 인증 토큰입니다.");
    }

    private Member createMember() {
        return new Member(
                1L,
                "hong",
                "password123",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "오전 교육 예정"
        );
    }

    private Map<?, ?> decodePayload(String token) {
        try {
            String payload = token.split("\\.")[1];
            byte[] decodedPayload = Base64.getUrlDecoder().decode(payload);
            return new ObjectMapper().readValue(decodedPayload, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
