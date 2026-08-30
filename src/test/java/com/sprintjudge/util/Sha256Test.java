package com.sprintjudge.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Sha256Test {

    @Test
    void knownEmptyHash() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(new byte[0]));
    }

    @Test
    void knownAbcHash() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deterministicAndNonTrivial() {
        String a = Sha256.hex("hello world");
        assertEquals(a, Sha256.hex("hello world"));
        assertEquals(64, a.length());
    }

    @Test
    void unicodeEncodingMatchesUtf8Bytes() {
        String h = Sha256.hex("héllo");
        assertEquals(h, Sha256.hex("héllo".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(h);
    }
}
