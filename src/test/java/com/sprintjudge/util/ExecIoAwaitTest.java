package com.sprintjudge.util;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecIoAwaitTest {

    private static Process finishedProc() {
        Process p = Mockito.mock(Process.class);
        try {
            Mockito.when(p.waitFor(100, TimeUnit.MILLISECONDS)).thenReturn(true);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    private static Process hangingProc() {
        Process p = Mockito.mock(Process.class);
        try {
            Mockito.when(p.waitFor(100, TimeUnit.MILLISECONDS)).thenReturn(false);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    @Test
    void awaitFinishedFast() throws Exception {
        Path out = Files.createTempFile("await", ".txt");
        Files.writeString(out, "hi");
        try {
            assertEquals(ExecIo.WaitOutcome.FINISHED, ExecIo.awaitBounded(finishedProc(), out, 5));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void awaitTimeoutWhenProcessHangs() throws Exception {
        Path out = Files.createTempFile("await", ".txt");
        try {
            assertEquals(ExecIo.WaitOutcome.TIMEOUT, ExecIo.awaitBounded(hangingProc(), out, 1));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void awaitTooBigWhenOutputExplodes() throws Exception {
        Path out = Files.createTempFile("await", ".big");
        Files.write(out, new byte[ExecIo.STDOUT_CAP_BYTES + 10]);
        try {
            assertEquals(ExecIo.WaitOutcome.TOO_BIG, ExecIo.awaitBounded(hangingProc(), out, 30));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void awaitMissingOutputFileKeepsWaiting() throws Exception {
        Path missing = Path.of("no-such-await-output-xyz-" + System.nanoTime() + ".txt");
        assertEquals(ExecIo.WaitOutcome.TIMEOUT, ExecIo.awaitBounded(hangingProc(), missing, 1));
    }

    @Test
    void awaitZeroTimeoutMeansAtLeastOneSecond() throws Exception {
        Path out = Files.createTempFile("await", ".txt");
        long start = System.nanoTime();
        try {
            assertEquals(ExecIo.WaitOutcome.TIMEOUT, ExecIo.awaitBounded(hangingProc(), out, 0));
        } finally {
            Files.deleteIfExists(out);
        }
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(900));
    }

    @Test
    void awaitInterruptedKillsAndTimesOut() throws Exception {
        Process p = Mockito.mock(Process.class);
        Mockito.when(p.waitFor(100, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("stop"));
        Path out = Files.createTempFile("await", ".txt");
        boolean interrupted = false;
        try {
            assertEquals(ExecIo.WaitOutcome.TIMEOUT, ExecIo.awaitBounded(p, out, 30));
            interrupted = Thread.currentThread().isInterrupted();
        } finally {
            Files.deleteIfExists(out);
            if (interrupted) Thread.interrupted();
        }
        Mockito.verify(p).destroyForcibly();
        assertTrue(interrupted);
    }

    @Test
    void killAndReapDestroys() throws Exception {
        Process p = Mockito.mock(Process.class);
        Mockito.when(p.waitFor(5, TimeUnit.SECONDS)).thenReturn(true);
        ExecIo.killAndReap(p);
        Mockito.verify(p).destroyForcibly();
    }

    @Test
    void killAndReapNullIsNoop() {
        ExecIo.killAndReap(null);
    }

    @Test
    void killAndReapInterruptedRestoresFlag() throws Exception {
        Process p = Mockito.mock(Process.class);
        Mockito.when(p.waitFor(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("stop"));
        ExecIo.killAndReap(p);
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void readCappedStreamExactlyAtCap() {
        byte[] data = new byte[ExecIo.STDOUT_CAP_BYTES];
        java.util.Arrays.fill(data, (byte) 'a');
        String s = ExecIo.readCappedStream(new java.io.ByteArrayInputStream(data));
        assertTrue(s != null && s.length() == ExecIo.STDOUT_CAP_BYTES);
    }

    @Test
    void readCappedStreamEmpty() {
        assertEquals("", ExecIo.readCappedStream(new java.io.ByteArrayInputStream(new byte[0])));
    }

    @Test
    void readCappedFileExactlyAtCap() throws Exception {
        Path f = Files.createTempFile("execio", ".cap");
        byte[] data = new byte[ExecIo.STDOUT_CAP_BYTES];
        java.util.Arrays.fill(data, (byte) 'b');
        Files.write(f, data);
        try {
            assertTrue(ExecIo.readCappedFile(f) != null);
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void readCappedFileEmpty() throws Exception {
        Path f = Files.createTempFile("execio", ".empty");
        try {
            assertEquals("", ExecIo.readCappedFile(f));
        } finally {
            Files.deleteIfExists(f);
        }
    }

    @Test
    void deleteTreeOnSingleFile() throws Exception {
        Path f = Files.createTempFile("execio", ".one");
        ExecIo.deleteTree(f);
        assertFalse(Files.exists(f));
    }

    @Test
    void deleteTreeRemovesDeepNesting() throws Exception {
        Path root = Files.createTempDirectory("execio-deep");
        Path deep = root.resolve("a").resolve("b").resolve("c");
        Files.createDirectories(deep);
        Files.writeString(deep.resolve("f.txt"), "x");
        Files.writeString(root.resolve("top.txt"), "y");
        ExecIo.deleteTree(root);
        assertFalse(Files.exists(root));
    }

    @Test
    void realProcessAwaitFinished() throws Exception {
        Process p = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
        Path out = Files.createTempFile("await", ".txt");
        Files.writeString(out, "v");
        try {
            assertEquals(ExecIo.WaitOutcome.FINISHED, ExecIo.awaitBounded(p, out, 30));
        } finally {
            Files.deleteIfExists(out);
        }
        Thread.interrupted();
    }
}
