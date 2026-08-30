package com.sprintjudge.service.executor;

import com.sprintjudge.util.ExecIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractScriptExecutorTest {

    /** Package-private subclass exercising every concrete/template branch of the base. */
    static final class StubExecutor extends AbstractScriptExecutor {
        private volatile List<String> forced;

        StubExecutor(String workDirBase, String scriptsDir, int defaultTimeoutSec) {
            this.workDirBase = workDirBase;
            this.scriptsDir = scriptsDir;
            this.defaultTimeoutSec = defaultTimeoutSec;
        }

        void force(List<String> cmd) { this.forced = cmd; }

        @Override
        protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
            if (forced != null) return forced;
            return switch (language) {
                case "node", "python" -> List.of("cmd", "/c", "type", "\"" + inputFile + "\"");
                default -> throw new UnsupportedOperationException(language);
            };
        }
    }

    private static JudgeRequest req(String language, String source, List<TestCase> tcs, int timeout) {
        return new JudgeRequest(language, source, tcs, timeout, 0);
    }

    private static TestCase tc(String input, String expected) {
        return new TestCase(input, expected, false);
    }

    @Test
    void supportsRecognisesCanonicalLanguages() {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        assertTrue(ex.supports("node"));
        assertTrue(ex.supports("python"));
        assertTrue(ex.supports("c"));
        assertTrue(ex.supports("cpp"));
        assertTrue(ex.supports("java"));
        // canonical mapping
        assertTrue(ex.supports("javascript"));
        assertTrue(ex.supports("js"));
        assertTrue(ex.supports("py"));
        assertFalse(ex.supports("ruby"));
        assertFalse(ex.supports(null)); // canonical(null) -> "" -> unsupported
    }

    @Test
    void scriptPathBuildsLanguageScript() {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        assertEquals(Path.of("sd", "node.sh"), ex.scriptPath("node"));
    }

    @Test
    void unsupportedLanguageReturnsErrorCase() {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        JudgeResult r = ex.judge(req("ruby", "x", List.of(tc("in", "out")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals(1, r.cases().size());
        assertEquals("unsupported_language", r.cases().get(0).error());
    }

    @Test
    void runDirCreationFailureReturnsEmptyResult(@TempDir Path tempDir) throws Exception {
        Path blocker = tempDir.resolve("blocker");
        Files.createFile(blocker); // a regular file blocks createDirectories underneath it
        StubExecutor ex = new StubExecutor(blocker.toString(), tempDir.toString(), 30);
        JudgeResult r = ex.judge(req("node", "x", List.of(tc("in", "out")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals(0, r.cases().size());
    }

    @Test
    void successWhenOutputMatchesExpected(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        JudgeResult r = ex.judge(req("node", "print(5)",
                List.of(tc("5", "5"), tc("42", "42")), 10));
        assertEquals(2, r.passed());
        assertTrue(r.allPassed());
        assertEquals(2, r.cases().size());
        assertTrue(r.cases().get(0).passed());
    }

    @Test
    void canonicalAliasRunsThroughCommandFor(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        JudgeResult r = ex.judge(req("javascript", "ignored", List.of(tc("9", "9")), 10));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void mismatchRecordsError(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        JudgeResult r = ex.judge(req("python", "x", List.of(tc("5", "6")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void nullSourceInputExpectedAreHandled(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        JudgeResult r = ex.judge(req("node", null,
                List.of(tc(null, null)), 10));
        // empty output vs empty expected -> passes
        assertEquals(1, r.passed());
        assertTrue(r.cases().get(0).passed());
    }

    @Test
    void timeoutBranchKillsProcess() throws Exception {
        Path work = Files.createTempDirectory("oq-timeout");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(List.of("ping", "-n", "6", "127.0.0.1"));
            JudgeResult r = ex.judge(req("node", "x", List.of(tc("in", "out")), 0));
            assertEquals(0, r.passed());
            assertEquals("timeout", r.cases().get(0).error());
        } finally {
            Thread.sleep(200); // let the killed process release its output-file handle
            ExecIo.deleteTree(work);
        }
    }

    @Test
    void stdoutExceededBranch(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        String big = "x".repeat(2 * ExecIo.STDOUT_CAP_BYTES);
        JudgeResult r = ex.judge(req("node", "x", List.of(tc(big, "out")), 10));
        assertEquals(0, r.passed());
        assertEquals("stdout_exceeded_1MB", r.cases().get(0).error());
    }

    @Test
    void unsupportedInCommandForThrowsAndCleansUp(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        for (String lang : new String[]{"c", "cpp", "java"}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> ex.judge(req(lang, "x", List.of(tc("in", "out")), 10)));
        }
    }

    @Test
    void processStartIOExceptionIsCaught(@TempDir Path tempDir) {
        StubExecutor ex = new StubExecutor(tempDir.toString(), tempDir.toString(), 30);
        ex.force(List.of("this-program-does-not-exist-xyz123"));
        JudgeResult r = ex.judge(req("node", "x", List.of(tc("in", "out")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals(0, r.cases().size());
    }

    @Test
    void extensionDefaultReturnsTxtViaReflection() throws Exception {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        Method m = AbstractScriptExecutor.class.getDeclaredMethod("extension", String.class);
        m.setAccessible(true);
        assertEquals(".txt", m.invoke(ex, "ruby"));
        assertEquals(".txt", m.invoke(ex, ""));
    }
}
