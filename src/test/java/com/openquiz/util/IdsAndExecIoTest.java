package com.openquiz.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsAndExecIoTest {

    private static final Pattern UUID_SHAPE =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // ---------- Ids.uuid ----------

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void uuidShapeIsValid(int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(UUID_SHAPE.matcher(Ids.uuid()).matches());
        }
    }

    @Test
    void uuidsAreUniqueAcrossFiveHundredDraws() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String id = Ids.uuid();
            assertNotEquals(id, "", "empty uuid");
            assertTrue(seen.add(id), "duplicate uuid: " + id);
        }
    }

    // ---------- Ids.pin ----------

    @ParameterizedTest
    @ValueSource(ints = {1, 25, 200})
    void pinIsSixDigitsInRange(int draws) {
        for (int i = 0; i < draws; i++) {
            String pin = Ids.pin();
            assertEquals(6, pin.length());
            assertTrue(pin.matches("\\d{6}"));
            int value = Integer.parseInt(pin);
            assertTrue(value >= 100000 && value <= 999999, "out of range: " + pin);
        }
    }

    @Test
    void pinsVaryInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) seen.add(Ids.pin());
        assertTrue(seen.size() > 40, "pin generation looks constant");
    }

    // ---------- ExecIo.readCappedStream ----------

    @Test
    void readsSmallStreamTrimmed() {
        String out = ExecIo.readCappedStream(
                new ByteArrayInputStream("  hello \n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hello", out);
    }

    @Test
    void emptyStreamGivesEmptyString() {
        assertEquals("", ExecIo.readCappedStream(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void exactlyAtCapStillReads() {
        byte[] data = new byte[ExecIo.STDOUT_CAP_BYTES];
        java.util.Arrays.fill(data, (byte) 'x');
        assertNotNull(ExecIo.readCappedStream(new ByteArrayInputStream(data)));
    }

    @Test
    void oneByteOverCapSignalsNull() {
        byte[] data = new byte[ExecIo.STDOUT_CAP_BYTES + 1];
        java.util.Arrays.fill(data, (byte) 'y');
        assertNull(ExecIo.readCappedStream(new ByteArrayInputStream(data)));
    }

    @Test
    void readCappedOnProcessDelegates() throws IOException {
        Process proc = new ProcessBuilder(getEchoCommand())
                .redirectErrorStream(true).start();
        assertEquals("pong", ExecIo.readCapped(proc));
    }

    /** Cross-platform echo so this test runs on Windows and Linux. */
    private static java.util.List<String> getEchoCommand() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        return win ? java.util.List.of("cmd", "/c", "echo pong")
                   : java.util.List.of("echo", "pong");
    }

    @Test
    void halfMegabyteStreamReadsCleanly() {
        byte[] data = new byte[512 * 1024];
        java.util.Arrays.fill(data, (byte) 'z');
        String out = ExecIo.readCappedStream(new ByteArrayInputStream(data));
        assertEquals(512 * 1024, out.length());
    }

    @Test
    void uuidThousandDrawsAllUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) seen.add(Ids.uuid());
        assertEquals(1000, seen.size());
    }

    // ---------- ExecIo.deleteTree ----------

    @Test
    void deleteTreeRemovesNestedContent() throws IOException {
        Path root = Files.createTempDirectory("oq-deltest");
        Path nested = root.resolve("a/b/c");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("f.txt"), "x");
        Files.writeString(root.resolve("top.txt"), "y");
        ExecIo.deleteTree(root);
        assertTrue(!Files.exists(root));
    }

    @Test
    void deleteTreeToleratesMissingPath() {
        ExecIo.deleteTree(Path.of(System.getProperty("java.io.tmpdir"), "does-not-exist-oq"));
    }
}
