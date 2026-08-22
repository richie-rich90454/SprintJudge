package com.openquiz.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Allocation-light SHA-256 hex digests for content-addressed compile caching.
 */
public final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });

    private Sha256() {}

    public static String hex(byte[] data) {
        MessageDigest md = DIGEST.get();
        md.reset();
        byte[] digest = md.digest(data);
        char[] out = new char[digest.length * 2];
        for (int i = 0, j = 0; i < digest.length; i++) {
            int b = digest[i] & 0xFF;
            out[j++] = HEX[b >>> 4];
            out[j++] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    public static String hex(String utf8) {
        return hex(utf8.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
