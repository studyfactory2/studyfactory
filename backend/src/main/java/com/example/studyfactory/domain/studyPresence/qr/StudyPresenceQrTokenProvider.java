package com.example.studyfactory.domain.studyPresence.qr;

import com.example.studyfactory.domain.studyPresence.exception.StudyPresenceException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StudyPresenceQrTokenProvider {

    private static final String VERSION = "sfqr1";
    private static final String PURPOSE = "study-presence";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int SIGNATURE_BYTES = 32;
    private static final int MAXIMUM_TOKEN_LENGTH = 128;

    private final byte[] secret;

    public StudyPresenceQrTokenProvider(@Value("${study-presence.qr.secret}") String encodedSecret) {
        this.secret = decodeSecret(encodedSecret);
    }

    public String createToken(Long branchId) {
        validateBranchId(branchId);
        String branch = branchId.toString();
        String unsignedToken = VERSION + "." + branch;
        return unsignedToken + "." + encode(sign(signingValue(branch)));
    }

    public Long getBranchId(String token) {
        try {
            if (token == null || token.isBlank() || token.length() > MAXIMUM_TOKEN_LENGTH) {
                throw StudyPresenceException.invalidQrToken();
            }

            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw StudyPresenceException.invalidQrToken();
            }

            Long branchId = parseCanonicalBranchId(parts[1]);
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expectedSignature = sign(signingValue(parts[1]));
            if (providedSignature.length != SIGNATURE_BYTES
                    || !encode(providedSignature).equals(parts[2])
                    || !MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw StudyPresenceException.invalidQrToken();
            }

            return branchId;
        } catch (StudyPresenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw StudyPresenceException.invalidQrToken();
        }
    }

    private Long parseCanonicalBranchId(String value) {
        Long branchId = Long.valueOf(value);
        validateBranchId(branchId);
        if (!branchId.toString().equals(value)) {
            throw StudyPresenceException.invalidQrToken();
        }
        return branchId;
    }

    private void validateBranchId(Long branchId) {
        if (branchId == null || branchId <= 0) {
            throw StudyPresenceException.invalidQrToken();
        }
    }

    private String signingValue(String branchId) {
        return PURPOSE + "|" + VERSION + "|" + branchId;
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw StudyPresenceException.invalidQrToken();
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decodedSecret = Base64.getDecoder().decode(encodedSecret);
            if (decodedSecret.length < MINIMUM_SECRET_BYTES) {
                throw invalidSecretConfiguration();
            }
            return decodedSecret;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidSecretConfiguration();
        }
    }

    private IllegalStateException invalidSecretConfiguration() {
        return new IllegalStateException(
                "study-presence.qr.secret must be Base64-encoded and contain at least 32 bytes"
        );
    }
}
