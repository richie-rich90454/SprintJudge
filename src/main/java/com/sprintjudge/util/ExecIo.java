package com.openquiz.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared process-I/O helpers for every executor implementation.
 */
public final class ExecIo {

    /** Edge case X: captured stdout is capped at 1MB per test case. */
    public static final int STDOUT_CAP_BYTES = 1_048_576;

    private ExecIo() {}

    /**
     * Reads stdout up to {@link #STDOUT_CAP_BYTES}. Returns null when the
     * limit is exceeded (caller must kill the process).
     */
    public static String readCapped(Process proc) {
        return readCappedStream(proc.getInputStream());
    }

    /** Stream-level variant, directly unit-testable without spawning processes. */
    public static String readCappedStream(InputStream in) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > STDOUT_CAP_BYTES) return null;
                baos.write(buf, 0, n);
            }
        } catch (IOException e) {
            return "";
        }
        return baos.toString(StandardCharsets.UTF_8).trim();
    }

    /** Best-effort recursive delete of a run directory. */
    public static void deleteTree(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
