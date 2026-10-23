package io.tallyard.tenancy.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Key format: {@code ty_<40 base62 chars>}. The first 11 characters form the public prefix used in
 * listings and logs; the SHA-256 of the whole key is what the database stores.
 */
final class ApiKeyCodec {

    static final String KEY_PREFIX = "ty_";
    static final int PUBLIC_PREFIX_LENGTH = 11;
    private static final int SECRET_LENGTH = 40;
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyCodec() {}

    static String generate() {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    static String publicPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(PUBLIC_PREFIX_LENGTH, rawKey.length()));
    }

    static boolean looksLikeKey(String candidate) {
        return candidate != null
                && candidate.startsWith(KEY_PREFIX)
                && candidate.length() == KEY_PREFIX.length() + SECRET_LENGTH
                && candidate.substring(KEY_PREFIX.length()).chars().allMatch(Character::isLetterOrDigit);
    }

    static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
