package com.bcp.workshop.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    private static final String HASH_PREFIX = "pbkdf2-sha256";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int MINIMUM_ACCEPTED_ITERATIONS = 100_000;
    private static final int MAXIMUM_ACCEPTED_ITERATIONS = 1_000_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int DERIVED_KEY_LENGTH_BITS = 256;
    private static final int HASH_PARTS_COUNT = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private PasswordHasher() {}

    static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derivedKey = derive(password, salt, ITERATIONS);
        return HASH_PREFIX + "$" + ITERATIONS + "$"
                + BASE64_ENCODER.encodeToString(salt) + "$"
                + BASE64_ENCODER.encodeToString(derivedKey);
    }

    static boolean verify(String password, String storedHash) {
        if (password == null) {
            return false;
        }

        ParsedHash parsedHash = parse(storedHash);
        if (parsedHash == null) {
            return false;
        }

        return MessageDigest.isEqual(parsedHash.derivedKey(),
                derive(password, parsedHash.salt(), parsedHash.iterations()));
    }

    static boolean isEncoded(String storedPassword) {
        return parse(storedPassword) != null;
    }

    private static ParsedHash parse(String storedHash) {
        if (storedHash == null) {
            return null;
        }

        String[] hashParts = storedHash.split("\\$", -1);
        if (hashParts.length != HASH_PARTS_COUNT || !HASH_PREFIX.equals(hashParts[0])) {
            return null;
        }

        try {
            int iterations = Integer.parseInt(hashParts[1]);
            byte[] salt = BASE64_DECODER.decode(hashParts[2]);
            byte[] derivedKey = BASE64_DECODER.decode(hashParts[3]);
            if (iterations < MINIMUM_ACCEPTED_ITERATIONS || iterations > MAXIMUM_ACCEPTED_ITERATIONS
                    || salt.length != SALT_LENGTH_BYTES
                    || derivedKey.length != DERIVED_KEY_LENGTH_BITS / Byte.SIZE) {
                return null;
            }
            return new ParsedHash(iterations, salt, derivedKey);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, DERIVED_KEY_LENGTH_BITS);
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            return keyFactory.generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            keySpec.clearPassword();
        }
    }

    private record ParsedHash(int iterations, byte[] salt, byte[] derivedKey) {}
}