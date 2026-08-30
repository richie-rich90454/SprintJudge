package com.sprintjudge.util;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExecIoTest {

    @Test
    void readCappedFileReturnsTrimmedContent() throws IOException {
        Path f = Files.createTempFile("execio", ".txt");
        Files.writeString(f, "  hello world  \n");
        assertEquals("hello world", ExecIo.readCappedFile(f));
        Files.deleteIfExists(f);
    }

    @Test
    void readCappedFileOverCapReturnsNull() throws IOException {
        Path f = Files.createTempFile("execio", ".big");
        byte[] data = new byte[ExecIo.STDOUT_CAP_BYTES + 1];
        Files.write(f, data);
        assertNull(ExecIo.readCappedFile(f));
        Files.deleteIfExists(f);
    }

    @Test
    void readCappedFileOnDirectoryReturnsEmpty() throws IOException {
        Path dir = Files.createTempDirectory("execio-dir");
        // Files.size on a dir is < cap; readString throws IOException -> ""
        assertEquals("", ExecIo.readCappedFile(dir));
        Files.deleteIfExists(dir);
    }

    @Test
    void readCappedFileMissingReturnsEmpty() {
        assertEquals("", ExecIo.readCappedFile(Path.of("no-such-file-xyz-12345.txt")));
    }

    @Test
    void readCappedStreamIoExceptionReturnsEmpty() throws IOException {
        InputStream in = Mockito.mock(InputStream.class);
        Mockito.when(in.read(Mockito.any(byte[].class))).thenThrow(new IOException("boom"));
        assertEquals("", ExecIo.readCappedStream(in));
    }

    @Test
    void readCappedReadsRealProcessStdout() throws Exception {
        Process p = new ProcessBuilder("cmd", "/c", "echo", "OpenQuiz").start();
        p.waitFor();
        assertEquals("OpenQuiz", ExecIo.readCapped(p));
    }

    @Test
    void readCappedViaMockedProcess() {
        Process p = Mockito.mock(Process.class);
        Mockito.when(p.getInputStream())
                .thenReturn(new ByteArrayInputStream("cap".getBytes(StandardCharsets.UTF_8)));
        assertEquals("cap", ExecIo.readCapped(p));
    }

    @Test
    void readCappedStreamHappyTrims() {
        InputStream in = new ByteArrayInputStream("  hello  ".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", ExecIo.readCappedStream(in));
    }

    @Test
    void readCappedStreamOverCapReturnsNull() throws IOException {
        InputStream in = Mockito.mock(InputStream.class);
        Mockito.when(in.read(Mockito.any(byte[].class))).thenReturn(8192);
        assertNull(ExecIo.readCappedStream(in));
    }

    @Test
    void deleteTreeRemovesNestedDirectory() throws IOException {
        Path dir = Files.createTempDirectory("execio-del");
        Path file = dir.resolve("child.txt");
        Files.writeString(file, "x");
        ExecIo.deleteTree(dir);
        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteTreeSwallowsWalkIOExceptionOnMissingDir() {
        Path missing = Path.of("no-such-dir-xyz-98765");
        ExecIo.deleteTree(missing); // Files.walk throws -> outer catch covered
        assertFalse(Files.exists(missing));
    }

    @Test
    void deleteTreeSwallowsIoExceptionOnLockedFile() throws IOException {
        Path dir = Files.createTempDirectory("execio-lock");
        Path file = dir.resolve("locked.txt");
        Files.writeString(file, "x");
        try (FileOutputStream os = new FileOutputStream(file.toFile())) {
            ExecIo.deleteTree(dir); // deleteIfExists throws -> inner catch covered
        }
        ExecIo.deleteTree(dir); // cleanup after the handle is released
        assertFalse(Files.exists(dir));
    }
}
