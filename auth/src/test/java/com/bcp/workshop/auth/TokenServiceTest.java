package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTest {
    private static final String TEST_SIGNING_KEY = "test-only-auth-signing-key-not-for-real-use";
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder TOKEN_DECODER = Base64.getUrlDecoder();

    private final TokenService tokenService = new TokenService(
            TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8));

    @Test
    void verifiesIssuedToken() {
        String token = tokenService.create("student", "STUDENT");

        TokenService.TokenClaims claims = tokenService.verify(token);

        assertEquals("student", claims.username());
        assertEquals("STUDENT", claims.role());
    }

    @Test
    void acceptsSigningKeyFromConfigurationConstructor() {
        TokenService configuredService = new TokenService(TEST_SIGNING_KEY);

        assertEquals("student",
                configuredService.verify(configuredService.create("student", "STUDENT")).username());
    }

    @Test
    void rejectsTokenWhenUsernameIsAltered() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.verify(alterClaims("student", "OTHER", null)));
    }

    @Test
    void rejectsTokenWhenRoleIsAltered() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.verify(alterClaims(null, "ADMIN", null)));
    }

    @Test
    void rejectsTokenWhenExpiryIsAltered() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.verify(alterClaims(null, null, Long.MAX_VALUE)));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = alterClaimsAndSign(null, null, 1L);

        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(token));
    }

    @Test
    void rejectsSigningKeyShorterThanMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new TokenService(new byte[31]));
    }

    @Test
    void rejectsNullMalformedAndUnsupportedTokens() {
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(null));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("malformed"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("v2.payload.signature"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify("v1.!!.signature"));
    }

    @Test
    void rejectsMalformedAndTrailingClaims() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(signClaims(new byte[]{1, 2})));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("student");
            output.writeUTF("STUDENT");
            output.writeLong(Long.MAX_VALUE);
            output.writeByte(1);
        }
        assertThrows(IllegalArgumentException.class, () -> tokenService.verify(signClaims(bytes.toByteArray())));
    }

    @Test
    void rejectsClaimsThatCannotBeEncoded() {
        assertThrows(IllegalStateException.class,
                () -> tokenService.create("x".repeat(65_536), "STUDENT"));
    }

    private String alterClaims(String username, String role, Long expiresAt) throws Exception {
        return alterClaimsAndSign(username, role, expiresAt, false);
    }

    private String alterClaimsAndSign(String username, String role, Long expiresAt) throws Exception {
        return alterClaimsAndSign(username, role, expiresAt, true);
    }

    private String alterClaimsAndSign(String username, String role, Long expiresAt, boolean sign) throws Exception {
        String[] parts = tokenService.create("student", "STUDENT").split("\\.");
        byte[] originalClaims = TOKEN_DECODER.decode(parts[1]);
        String originalUsername;
        String originalRole;
        long originalExpiry;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(originalClaims))) {
            originalUsername = input.readUTF();
            originalRole = input.readUTF();
            originalExpiry = input.readLong();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(username == null ? originalUsername : username);
            output.writeUTF(role == null ? originalRole : role);
            output.writeLong(expiresAt == null ? originalExpiry : expiresAt);
        }

        String encodedClaims = TOKEN_ENCODER.encodeToString(bytes.toByteArray());
        if (!sign) {
            return parts[0] + "." + encodedClaims + "." + parts[2];
        }

        return signClaims(bytes.toByteArray());
    }

    private String signClaims(byte[] claims) throws Exception {
        String encodedClaims = TOKEN_ENCODER.encodeToString(claims);
        String signingInput = "v1." + encodedClaims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = TOKEN_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }
}
