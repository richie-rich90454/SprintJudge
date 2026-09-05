package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sha256ExtraTest {

    @Test
    void knownHelloHash() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                Sha256.hex("hello"));
    }

    @Test
    void knownFoxHash() {
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                Sha256.hex("The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void outputIsLowercaseHex64() {
        String h = Sha256.hex("OpenQuiz");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"), h);
    }

    @Test
    void distinctInputsDiffer() {
        assertNotEquals(Sha256.hex("a"), Sha256.hex("b"));
        assertNotEquals(Sha256.hex("python|x"), Sha256.hex("c|x"));
    }

    @Test
    void byteAndStringOverloadsAgree() {
        byte[] raw = {0, 1, 2, (byte) 255, 17};
        String asLatin1 = new String(raw, StandardCharsets.ISO_8859_1);
        assertEquals(Sha256.hex(raw), Sha256.hex(asLatin1.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void reusableAcrossCalls() {
        String first = Sha256.hex("first");
        String second = Sha256.hex("second");
        assertEquals(first, Sha256.hex("first"));
        assertEquals(second, Sha256.hex("second"));
        assertNotEquals(first, second);
    }

    @Test
    void longInputHashes() {
        String big = "z".repeat(100_000);
        assertEquals(64, Sha256.hex(big).length());
        assertEquals(Sha256.hex(big), Sha256.hex(big));
    }

    @Test
    void missingAlgorithmWrapsOnFreshThread() throws Exception {
        // SHA-256 is mandatory on every JVM, so the only way to reach the
        // catch is to pull the provider on a thread whose ThreadLocal has
        // never initialized. Skip if another provider still serves it.
        java.security.Provider sun = java.security.Security.getProvider("SUN");
        org.junit.jupiter.api.Assumptions.assumeTrue(sun != null);
        java.security.Security.removeProvider("SUN");
        try {
            org.junit.jupiter.api.Assumptions.assumeTrue(!providesSha256());
            java.util.concurrent.atomic.AtomicReference<Throwable> err =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Thread t = new Thread(() -> {
                try {
                    Sha256.hex("x");
                } catch (Throwable th) {
                    err.set(th);
                }
            });
            t.start();
            t.join(5_000);
            assertTrue(err.get() instanceof IllegalStateException);
        } finally {
            if (java.security.Security.getProvider("SUN") == null) {
                java.security.Security.addProvider(sun);
            }
        }
    }

    private static boolean providesSha256() {
        try {
            java.security.MessageDigest.getInstance("SHA-256");
            return true;
        } catch (java.security.NoSuchAlgorithmException e) {
            return false;
        }
    }
}
