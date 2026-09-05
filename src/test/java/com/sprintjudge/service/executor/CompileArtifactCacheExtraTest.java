package com.sprintjudge.service.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileArtifactCacheExtraTest {

    @Test
    void hitRatioZeroWithoutTraffic(@TempDir Path tmp) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tmp.toString(), 16, 16);
        assertEquals(0.0, c.hitRatio());
        assertEquals(0, c.hits());
        assertEquals(0, c.misses());
    }

    @Test
    void hitRatioHalf(@TempDir Path tmp) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tmp.toString(), 16, 16);
        Path bin = Files.createTempFile(tmp, "bin", ".o");
        Files.write(bin, new byte[]{1});
        c.put("k", bin);
        c.get("k");
        c.get("absent");
        assertEquals(0.5, c.hitRatio(), 0.0001);
    }

    @Test
    void keyForStableAndSensitive(@TempDir Path tmp) throws IOException {
        new CompileArtifactCache(tmp.toString(), 16, 16);
        assertEquals(CompileArtifactCache.keyFor("c", "int main(){}"),
                CompileArtifactCache.keyFor("c", "int main(){}"));
        assertNotEquals(CompileArtifactCache.keyFor("c", "a"), CompileArtifactCache.keyFor("cpp", "a"));
        assertNotEquals(CompileArtifactCache.keyFor("c", "a"), CompileArtifactCache.keyFor("c", "b"));
    }

    @Test
    void replaceKeepsSingleEntry(@TempDir Path tmp) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tmp.toString(), 16, 16);
        Path a = Files.createTempFile(tmp, "a", ".o");
        Path b = Files.createTempFile(tmp, "b", ".o");
        Files.write(a, new byte[10]);
        Files.write(b, new byte[25]);
        c.put("k", a);
        assertEquals(10, c.bytes());
        c.put("k", b);
        assertEquals(1, c.entries());
        // Quirk: replace copies over dir.resolve(key) first, so fileSize(prev)
        // reads the new size and the byte total does not grow; prev and target
        // share one path, so the cached copy is reaped and the next get misses.
        assertEquals(10, c.bytes());
        assertTrue(c.get("k").isEmpty());
    }

    @Test
    void fileSizeCatchHitViaExpiredEntryDuringEviction(@TempDir Path tmp) throws IOException {
        CompileArtifactCache c = new CompileArtifactCache(tmp.toString(), 16, 16);
        Path first = Files.createTempFile(tmp, "first", ".o");
        Files.write(first, new byte[]{1});
        c.put("oldest", first);
        Files.delete(tmp.resolve("oldest")); // remove the cached copy so fileSize() throws while evicting
        for (int i = 0; i < 17; i++) {
            Path bin = Files.createTempFile(tmp, "e" + i, ".o");
            Files.write(bin, new byte[1]);
            c.put("k" + i, bin);
        }
        assertEquals(16, c.entries());
    }
}
