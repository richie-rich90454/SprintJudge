package com.sprintjudge.service.executor;

import com.sprintjudge.service.executor.AbstractScriptExecutorTest.StubExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractScriptExecutorRunTest {

    private static StubExecutor executor(Path work, Path scripts) {
        return new StubExecutor(work.toString(), scripts.toString(), 30);
    }

    private static RunRequest runReq(String language, String source, String stdin, int timeout) {
        return new RunRequest(language, source, stdin, timeout);
    }

    private static JudgeRequest judgeReq(String language, String source, List<TestCase> tcs, int timeout) {
        return new JudgeRequest(language, source, tcs, timeout, 0);
    }

    @Test
    void runUnsupportedLanguage(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq("ruby", "x", "", 10));
        assertFalse(r.ok());
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void runNullLanguageIsUnsupported(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq(null, "x", "", 10));
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void runOversizedSource(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq("python", "x".repeat(65_537), "", 10));
        assertEquals("source_too_large", r.status());
    }

    @Test
    void runBoundarySizePassesGuard(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        var r = ex.run(runReq("node", "#" + "x".repeat(65_535), "", 10));
        assertTrue(r.ok() || r.status().equals("ok") || r.status().equals("runtime_error"));
    }

    @Test
    void runDirFailureIsIoError(@TempDir Path tmp) throws Exception {
        Path blocker = tmp.resolve("blocker");
        Files.createFile(blocker);
        StubExecutor ex = new StubExecutor(blocker.toString(), tmp.toString(), 30);
        assertEquals("io_error", ex.run(runReq("node", "x", "", 10)).status());
    }

    @Test
    void runSuccessEchoesInput(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq("node", "ignored", "9", 10));
        assertTrue(r.ok());
        assertEquals("9", r.output());
        assertEquals("ok", r.status());
    }

    @Test
    void runCanonicalAlias(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq("javascript", "ignored", "7", 10));
        assertTrue(r.ok());
        assertEquals("7", r.output());
    }

    @Test
    void runNullSourceAndStdinAreEmpty(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(runReq("python", null, null, 10));
        assertTrue(r.ok());
        assertEquals("", r.output());
    }

    @Test
    void runJavaFileNameUsesMain(@TempDir Path tmp) {
        AbstractScriptExecutor ex = new AbstractScriptExecutor() {
            {
                workDirBase = tmp.toString();
                scriptsDir = tmp.toString();
                defaultTimeoutSec = 30;
            }
            @Override
            protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
                assertTrue(sourceFile.getFileName().toString().equals("Main.java"));
                return List.of("cmd", "/c", "type", inputFile.toString());
            }
        };
        var r = ex.run(runReq("java", "ignored", "zz", 10));
        assertEquals("zz", r.output());
    }

    @Test
    void runNonZeroExitIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "exit 3"));
        var r = ex.run(runReq("node", "x", "", 10));
        assertFalse(r.ok());
        assertEquals("runtime_error", r.status());
    }

    @Test
    void runStartFailureIsIoError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("this-program-does-not-exist-xyz123"));
        assertEquals("io_error", ex.run(runReq("node", "x", "", 10)).status());
    }

    @Test
    void runTimeoutBranch(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-run-timeout");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(List.of("ping", "-n", "6", "127.0.0.1"));
            assertEquals("timeout", ex.run(runReq("node", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void runStdoutCapBranch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        String big = "x".repeat(2 * com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES);
        var r = ex.run(runReq("node", "x", big, 10));
        assertEquals("stdout_exceeded_1MB", r.status());
    }

    @Test
    void runFinishedOverCapBranch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        String justOver = "x".repeat(com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES + 4096);
        var r = ex.run(runReq("node", "x", justOver, 30));
        assertEquals("stdout_exceeded_1MB", r.status());
    }

    @Test
    void judgeFinishedOverCapBranch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        String justOver = "x".repeat(com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES + 4096);
        var r = ex.judge(judgeReq("node", "x",
                List.of(new TestCase(justOver, "out", false)), 30));
        assertEquals("stdout_exceeded_1MB", r.cases().get(0).error());
    }

    @Test
    void runTooBigOutcomeBranch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "for /l %i in (1,1,300000) do @echo "
                + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        var r = ex.run(runReq("node", "x", "", 30));
        assertEquals("stdout_exceeded_1MB", r.status());
    }

    @Test
    void judgeTooBigOutcomeBranch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "for /l %i in (1,1,300000) do @echo "
                + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        var r = ex.judge(judgeReq("node", "x", List.of(new TestCase("in", "out", false)), 30));
        assertEquals("stdout_exceeded_1MB", r.cases().get(0).error());
    }

    @Test
    void judgeSecondCaseStartFailureKillsPriorProc(@TempDir Path tmp) {
        AbstractScriptExecutor ex = new AbstractScriptExecutor() {
            {
                workDirBase = tmp.toString();
                scriptsDir = tmp.toString();
                defaultTimeoutSec = 30;
            }
            private int calls;
            @Override
            protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
                if (++calls == 1) return List.of("cmd", "/c", "type", inputFile.toString());
                return List.of("this-program-does-not-exist-xyz123");
            }
        };
        var r = ex.judge(judgeReq("node", "x",
                List.of(new TestCase("a", "a", false), new TestCase("b", "b", false)), 10));
        assertEquals(0, r.passed());
        assertTrue(r.cases().isEmpty());
    }

    @Test
    void judgeUsesDefaultTimeoutWhenZero(@TempDir Path tmp) {
        StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
        var r = ex.judge(judgeReq("node", "x",
                List.of(new TestCase("5", "5", false)), 0));
        assertEquals(1, r.passed());
    }

    @Test
    void judgeEmptyCasesAllPassed(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("node", "x", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.total());
    }

    @Test
    void extensionCoversAllLanguages() throws Exception {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        Method m = AbstractScriptExecutor.class.getDeclaredMethod("extension", String.class);
        m.setAccessible(true);
        assertEquals(".c", m.invoke(ex, "c"));
        assertEquals(".cpp", m.invoke(ex, "cpp"));
        assertEquals(".java", m.invoke(ex, "java"));
        assertEquals(".js", m.invoke(ex, "node"));
        assertEquals(".py", m.invoke(ex, "python"));
    }

    @Test
    void canonicalHandlesNullAndCase() {
        StubExecutor ex = new StubExecutor("wd", "sd", 30);
        assertEquals("python", ex.canonical("PY"));
        assertEquals("node", ex.canonical("JavaScript"));
        assertEquals("", ex.canonical(null));
        assertEquals("ruby", ex.canonical("ruby"));
    }

    @Test
    void supportsAllAndRejects(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        assertTrue(ex.supports("c"));
        assertTrue(ex.supports("cpp"));
        assertTrue(ex.supports("java"));
        assertFalse(ex.supports("go"));
    }

    @Test
    void judgeCommandForThrowingMidLoopPropagatesAndCleans(@TempDir Path tmp) {
        AbstractScriptExecutor ex = new AbstractScriptExecutor() {
            {
                workDirBase = tmp.toString();
                scriptsDir = tmp.toString();
                defaultTimeoutSec = 30;
            }
            private int calls;
            @Override
            protected List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir) {
                if (++calls == 2) throw new IllegalStateException("boom");
                return List.of("cmd", "/c", "type", inputFile.toString());
            }
        };
        try {
            ex.judge(judgeReq("node", "x",
                    List.of(new TestCase("a", "a", false), new TestCase("b", "b", false)), 10));
            assertTrue(false, "expected boom");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
    }
}
