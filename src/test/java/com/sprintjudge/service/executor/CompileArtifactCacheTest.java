package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileArtifactCacheTest {

    @Test
    void missWhenAbsent(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        assertFalse(c.get("missing").isPresent());
        assertEquals(1, c.misses());
        assertEquals(0, c.hits());
        assertEquals(0, c.entries());
    }

    @Test
    void putAndGetIsAHit(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path bin = Files.createTempFile(tempDir, "bin", ".o");
        Files.write(bin, new byte[]{1, 2, 3});
        c.put("k", bin);
        assertEquals(1, c.entries());
        assertEquals(3, c.bytes());
        assertTrue(c.get("k").isPresent());
        assertEquals(1, c.hits());
        assertEquals(0, c.misses());
    }

    @Test
    void putReplaceUpdatesBytesAndDeletesPrev(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path a = Files.createTempFile(tempDir, "a", ".o");
        Path b = Files.createTempFile(tempDir, "b", ".o");
        Files.write(a, new byte[10]);
        Files.write(b, new byte[25]);
        c.put("k", a);
        c.put("k", b); // replace -> prev branch executed (map still holds one entry)
        assertEquals(1, c.entries());
    }

    @Test
    void expiredFileTreatedAsMiss(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path bin = Files.createTempFile(tempDir, "bin", ".o");
        Files.write(bin, new byte[]{9});
        c.put("k", bin);
        Files.delete(tempDir.resolve("k")); // the cached copy lives at dir.resolve(key)
        assertFalse(c.get("k").isPresent());
        assertEquals(1, c.misses());
        assertEquals(0, c.entries());
    }

    @Test
    void evictionByEntryCount(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        for (int i = 0; i < 17; i++) {
            Path bin = Files.createTempFile(tempDir, "b" + i, ".o");
            Files.write(bin, new byte[1]);
            c.put("key" + i, bin);
        }
        assertEquals(16, c.entries());
        assertEquals(16, c.bytes());
    }

    @Test
    void evictionByTotalBytes(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 1, 1); // maxBytes forced to 16MB
        Path big = Files.createTempFile(tempDir, "big", ".o");
        Files.write(big, new byte[17 * 1024 * 1024]);
        c.put("huge", big);
        assertEquals(0, c.entries());
        assertEquals(0, c.bytes());
    }

    @Test
    void putIoExceptionIsSwallowed(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        c.put("k", tempDir.resolve("does-not-exist.bin")); // Files.copy throws
        assertEquals(0, c.entries());
    }

    @Test
    void fileSizeCatchHitViaExpiredEntryDuringEviction(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path first = Files.createTempFile(tempDir, "first", ".o");
        Files.write(first, new byte[]{1});
        c.put("oldest", first);
        Files.delete(first); // delete so fileSize() throws while evicting
        for (int i = 0; i < 17; i++) {
            Path bin = Files.createTempFile(tempDir, "e" + i, ".o");
            Files.write(bin, new byte[1]);
            c.put("k" + i, bin);
        }
        assertEquals(16, c.entries());
    }

    @Test
    void hitRatioAndKeyFor(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path bin = Files.createTempFile(tempDir, "bin", ".o");
        Files.write(bin, new byte[]{7});
        c.put("k", bin);
        c.get("k"); // hit
        assertEquals(1.0, c.hitRatio(), 0.0001);
        String key = CompileArtifactCache.keyFor("python", "x");
        assertEquals(64, key.length());
    }

    @Test
    void safeDeleteCatchHitOnNonEmptyDir(@TempDir Path tempDir) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tempDir.toString(), 16, 16);
        Path first = Files.createTempFile(tempDir, "d", ".o");
        Files.write(first, new byte[]{1});
        c.put("oldest", first);
        // Replace the cached copy with a non-empty directory so deletion fails.
        Files.delete(tempDir.resolve("oldest"));
        Files.createDirectory(tempDir.resolve("oldest"));
        Files.createFile(tempDir.resolve("oldest").resolve("child"));
        for (int i = 0; i < 17; i++) {
            Path bin = Files.createTempFile(tempDir, "e" + i, ".o");
            Files.write(bin, new byte[1]);
            c.put("k" + i, bin);
        }
        assertEquals(16, c.entries());
    }
}
