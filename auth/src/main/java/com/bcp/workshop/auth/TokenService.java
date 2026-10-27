package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class TokenService {
    private static final String TOKEN_VERSION = "v1";
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder TOKEN_DECODER = Base64.getUrlDecoder();
    static final int TOKEN_EXPIRY_SECONDS = 3600;

    private final byte[] secret;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.secret") String secret) {
        this(secret.getBytes(StandardCharsets.UTF_8));
    }

    TokenService(byte[] secret) {
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("Token signing key must be at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    String create(String username, String role) {
        long expiry = Instant.now().plusSeconds(TOKEN_EXPIRY_SECONDS).getEpochSecond();
        String encodedClaims = TOKEN_ENCODER.encodeToString(encodeClaims(username, role, expiry));
        String signingInput = TOKEN_VERSION + "." + encodedClaims;
        return signingInput + "." + TOKEN_ENCODER.encodeToString(sign(signingInput));
    }

    TokenClaims verify(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Token is invalid");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !TOKEN_VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Token is invalid");
        }

        byte[] claims;
        byte[] suppliedSignature;
        try {
            claims = TOKEN_DECODER.decode(parts[1]);
            suppliedSignature = TOKEN_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Token is invalid", error);
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
            throw new IllegalArgumentException("Token is invalid");
        }

        TokenClaims tokenClaims = decodeClaims(claims);
        if (tokenClaims.expiresAt() <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Token is expired");
        }
        return tokenClaims;
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret, SIGNATURE_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Token signing is unavailable", error);
        }
    }

    private static byte[] encodeClaims(String username, String role, long expiry) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(username);
                output.writeUTF(role);
                output.writeLong(expiry);
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Token claims could not be encoded", error);
        }
    }

    private static TokenClaims decodeClaims(byte[] encodedClaims) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encodedClaims))) {
            TokenClaims claims = new TokenClaims(input.readUTF(), input.readUTF(), input.readLong());
            if (input.available() != 0) {
                throw new IllegalArgumentException("Token is invalid");
            }
            return claims;
        } catch (IOException error) {
            throw new IllegalArgumentException("Token is invalid", error);
        }
    }

    record TokenClaims(String username, String role, long expiresAt) {}
}
