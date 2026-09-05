package com.sprintjudge.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

    /**
     * Reads a redirected output file after process exit. Returns null when the
     * cap is exceeded. Redirecting to a file avoids the classic pipe-buffer
     * deadlock where waitFor times out on a child blocked writing stdout.
     */
    public static String readCappedFile(Path file) {
        try {
            if (Files.size(file) > STDOUT_CAP_BYTES) return null;
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /** Best-effort recursive delete of a run directory. */
    public static void deleteTree(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    /** Bounded-wait outcome for a child writing to a redirected output file. */
    public enum WaitOutcome { FINISHED, TIMEOUT, TOO_BIG }

    /**
     * Waits up to {@code timeoutSec} for exit while watching the redirected
     * output file: a runaway writer is stopped at the cap instead of filling
     * the disk until the timeout kill. Missing file reads as size 0.
     */
    public static WaitOutcome awaitBounded(Process proc, Path outputFile, long timeoutSec) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, timeoutSec));
        try {
            while (System.nanoTime() < deadline) {
                if (proc.waitFor(100, TimeUnit.MILLISECONDS)) return WaitOutcome.FINISHED;
                try {
                    if (Files.size(outputFile) > STDOUT_CAP_BYTES) return WaitOutcome.TOO_BIG;
                } catch (IOException ignored) {
                    // Not created yet — keep waiting.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killAndReap(proc);
            return WaitOutcome.TIMEOUT;
        }
        return WaitOutcome.TIMEOUT;
    }

    /** Destroys a child and reaps it so handles release before directory cleanup. */
    public static void killAndReap(Process proc) {
        if (proc == null) return;
        proc.destroyForcibly();
        try {
            proc.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
