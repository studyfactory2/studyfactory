package com.example.studyfactory.domain.studyPresence.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("학습실 출입 QR 토큰 제공자 테스트")
class StudyPresenceQrTokenProviderTest {

    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private StudyPresenceQrTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new StudyPresenceQrTokenProvider(encodedSecret("first-test-secret-with-at-least-32-bytes"));
    }

    @Test
    @DisplayName("지점 ID를 서명한 QR 토큰을 만들고 검증한다")
    void createAndValidateToken() {
        String token = tokenProvider.createToken(2L);

        assertThat(token).startsWith("sfqr1.2.");
        assertThat(tokenProvider.getBranchId(token)).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 지점과 비밀키에는 만료되지 않는 동일한 QR 토큰을 반환한다")
    void keepTokenStableForSameBranchAndSecret() {
        String firstToken = tokenProvider.createToken(2L);
        String secondToken = tokenProvider.createToken(2L);

        assertThat(secondToken).isEqualTo(firstToken);
    }

    @Test
    @DisplayName("지점 ID나 서명을 변경한 QR 토큰을 거절한다")
    void rejectTamperedToken() {
        String token = tokenProvider.createToken(2L);
        String changedBranch = token.replaceFirst("sfqr1\\.2\\.", "sfqr1.3.");
        String changedSignature = changeFirstSignatureCharacter(token);

        assertInvalidQr(changedBranch);
        assertInvalidQr(changedSignature);
    }

    @Test
    @DisplayName("같은 서명 바이트로 해석되는 비표준 Base64URL 표기를 거절한다")
    void rejectNonCanonicalSignatureEncoding() {
        String token = tokenProvider.createToken(2L);
        int lastIndex = token.length() - 1;
        int canonicalIndex = BASE64_URL_ALPHABET.indexOf(token.charAt(lastIndex));
        String nonCanonicalToken = token.substring(0, lastIndex)
                + BASE64_URL_ALPHABET.charAt(canonicalIndex + 1);

        assertInvalidQr(nonCanonicalToken);
    }

    @Test
    @DisplayName("다른 비밀키로 만든 QR 토큰을 거절한다")
    void rejectTokenSignedWithAnotherSecret() {
        StudyPresenceQrTokenProvider otherProvider = new StudyPresenceQrTokenProvider(
                encodedSecret("second-test-secret-with-at-least-32-bytes")
        );
        String token = otherProvider.createToken(2L);

        assertInvalidQr(token);
    }

    @Test
    @DisplayName("형식, 버전 또는 지점 ID가 잘못된 QR 토큰을 거절한다")
    void rejectMalformedToken() {
        List<String> invalidTokens = List.of(
                "",
                "sfqr1.1",
                "sfqr2.1.invalid-signature",
                "sfqr1.0.invalid-signature",
                "sfqr1.-1.invalid-signature",
                "sfqr1.01.invalid-signature",
                "sfqr1.1.not-base64!",
                "x".repeat(129)
        );

        invalidTokens.forEach(this::assertInvalidQr);
        assertThatThrownBy(() -> tokenProvider.getBranchId(null))
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("유효하지 않은 출입 QR 코드입니다.");
    }

    @Test
    @DisplayName("QR 비밀키가 Base64 형식이 아니거나 32바이트보다 짧으면 시작할 수 없다")
    void rejectInvalidSecretConfiguration() {
        assertThatThrownBy(() -> new StudyPresenceQrTokenProvider("not-base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new StudyPresenceQrTokenProvider(encodedSecret("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    private void assertInvalidQr(String token) {
        assertThatThrownBy(() -> tokenProvider.getBranchId(token))
                .as("token=%s", token)
                .isInstanceOf(StudyPresenceException.class)
                .hasMessageContaining("유효하지 않은 출입 QR 코드입니다.");
    }

    private String changeFirstSignatureCharacter(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private String encodedSecret(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
