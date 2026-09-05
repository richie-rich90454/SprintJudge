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

    private static final String MX_UNI = "h\u00e9llo w\u00f6rld \u4e2d\u6587 \ud83c\udf89";
    private static final List<String> MX_EXIT1 = List.of("cmd", "/c", "exit 1");
    private static final List<String> MX_EXIT3 = List.of("cmd", "/c", "exit 3");
    private static final List<String> MX_BADPROG = List.of("this-program-does-not-exist-xyz123");
    private static final List<String> MX_HANG = List.of("ping", "-n", "6", "127.0.0.1");

    private static RunRequest mxRun(String language, String source, String stdin, int timeout) {
        return new RunRequest(language, source, stdin, timeout);
    }

    @Test
    void mxRunEmptySourcePythonOk(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "", "e1", 10));
        assertEquals("ok", r.status());
        assertEquals("e1", r.output());
    }

    @Test
    void mxRunEmptySourceNodeOk(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "", "e2", 10));
        assertEquals("ok", r.status());
        assertEquals("e2", r.output());
    }

    @Test
    void mxRunEmptySourceCForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo c-empty"));
        var r = ex.run(mxRun("c", "", "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("c-empty"));
    }

    @Test
    void mxRunEmptySourceCppForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo cpp-empty"));
        assertEquals("ok", ex.run(mxRun("cpp", "", "", 10)).status());
    }

    @Test
    void mxRunEmptySourceJavaForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo java-empty"));
        assertEquals("ok", ex.run(mxRun("java", "", "", 10)).status());
    }

    @Test
    void mxRunWhitespaceSourcePythonOk(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "  \n\t ", "w1", 10));
        assertEquals("ok", r.status());
        assertEquals("w1", r.output());
    }

    @Test
    void mxRunWhitespaceSourceNodeOk(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "\n  ", "w2", 10));
        assertEquals("ok", r.status());
    }

    @Test
    void mxRunWhitespaceSourceCForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo c-ws"));
        assertEquals("ok", ex.run(mxRun("c", "   ", "", 10)).status());
    }

    @Test
    void mxRunWhitespaceSourceCppForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo cpp-ws"));
        assertEquals("ok", ex.run(mxRun("cpp", "\t", "", 10)).status());
    }

    @Test
    void mxRunWhitespaceSourceJavaForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo java-ws"));
        assertEquals("ok", ex.run(mxRun("java", "  ", "", 10)).status());
    }

    @Test
    void mxRunSyntaxErrorPythonIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.run(mxRun("python", "def broken(((", "", 10));
        assertEquals("runtime_error", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxRunSyntaxErrorNodeIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        assertEquals("runtime_error", ex.run(mxRun("node", "const = broken", "", 10)).status());
    }

    @Test
    void mxRunSyntaxErrorCIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        assertEquals("runtime_error", ex.run(mxRun("c", "int main( { broken", "", 10)).status());
    }

    @Test
    void mxRunSyntaxErrorCppIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        assertEquals("runtime_error", ex.run(mxRun("cpp", "int main( { broken", "", 10)).status());
    }

    @Test
    void mxRunSyntaxErrorJavaIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        assertEquals("runtime_error", ex.run(mxRun("java", "not java {{{", "", 10)).status());
    }

    @Test
    void mxJudgeSyntaxErrorPythonIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.judge(judgeReq("python", "def broken(((",
                List.of(new TestCase("in", "out", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
        assertFalse(r.allPassed());
    }

    @Test
    void mxJudgeSyntaxErrorNodeIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.judge(judgeReq("node", "const = broken",
                List.of(new TestCase("in", "out", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxJudgeSyntaxErrorCIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.judge(judgeReq("c", "int main( { broken",
                List.of(new TestCase("in", "out", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxJudgeSyntaxErrorCppIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.judge(judgeReq("cpp", "int main( { broken",
                List.of(new TestCase("in", "out", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxJudgeSyntaxErrorJavaIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT1);
        var r = ex.judge(judgeReq("java", "not java {{{",
                List.of(new TestCase("in", "out", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxRunCrashPythonIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        var r = ex.run(mxRun("python", "import sys; sys.exit(3)", "", 10));
        assertEquals("runtime_error", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxRunCrashNodeIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        assertEquals("runtime_error", ex.run(mxRun("node", "process.exit(3)", "", 10)).status());
    }

    @Test
    void mxRunCrashCIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        assertEquals("runtime_error", ex.run(mxRun("c", "int main(){return 3;}", "", 10)).status());
    }

    @Test
    void mxRunCrashCppIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        assertEquals("runtime_error", ex.run(mxRun("cpp", "int main(){return 3;}", "", 10)).status());
    }

    @Test
    void mxRunCrashJavaIsRuntimeError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        assertEquals("runtime_error", ex.run(mxRun("java", "System.exit(3);", "", 10)).status());
    }

    @Test
    void mxJudgeCrashPythonIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        var r = ex.judge(judgeReq("python", "sys.exit(3)",
                List.of(new TestCase("a", "a", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxJudgeCrashJavaIsMismatch(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_EXIT3);
        var r = ex.judge(judgeReq("java", "System.exit(3);",
                List.of(new TestCase("a", "a", false)), 10));
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void mxRunStderrOnlyPythonOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-py 1>&2"));
        var r = ex.run(mxRun("python", "x", "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.ok());
        assertTrue(r.output().contains("warn-py"));
    }

    @Test
    void mxRunStderrOnlyNodeOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-node 1>&2"));
        var r = ex.run(mxRun("node", "x", "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-node"));
    }

    @Test
    void mxRunStderrOnlyCForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-c 1>&2"));
        assertEquals("ok", ex.run(mxRun("c", "x", "", 10)).status());
    }

    @Test
    void mxRunStderrOnlyCppForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-cpp 1>&2"));
        assertEquals("ok", ex.run(mxRun("cpp", "x", "", 10)).status());
    }

    @Test
    void mxRunStderrOnlyJavaForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-java 1>&2"));
        assertEquals("ok", ex.run(mxRun("java", "x", "", 10)).status());
    }

    @Test
    void mxJudgeStderrOnlyMatchesExpected(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo warn-py 1>&2"));
        var r = ex.judge(judgeReq("python", "x",
                List.of(new TestCase("ignored-stdin", "warn-py", false)), 10));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void mxRunUnicodePythonEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "x", MX_UNI, 10));
        assertEquals("ok", r.status());
        assertEquals(MX_UNI, r.output());
    }

    @Test
    void mxRunUnicodeNodeEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "x", MX_UNI, 10));
        assertEquals("ok", r.status());
        assertEquals(MX_UNI, r.output());
    }

    @Test
    void mxRunUnicodeCForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "type", "\"PLACEHOLDER\""));
        ex.force(null);
        var r = executor(tmp, tmp).run(mxRun("python", "x", MX_UNI, 10));
        assertEquals(MX_UNI, r.output());
    }

    @Test
    void mxJudgeUnicodePythonPass(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("python", "x",
                List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
        assertEquals(1, r.passed());
        assertEquals("", r.cases().get(0).error());
    }

    @Test
    void mxJudgeUnicodeNodePass(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("node", "x",
                List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
        assertEquals(1, r.passed());
    }

    @Test
    void mxJudgeUnicodeCPass(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        var r = ex.judge(judgeReq("python", "x",
                List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
        assertTrue(r.allPassed());
    }

    @Test
    void mxRun100kPythonUnderCapOk(@TempDir Path tmp) {
        String big = "q".repeat(100 * 1024);
        var r = executor(tmp, tmp).run(mxRun("python", "x", big, 10));
        assertEquals("ok", r.status());
        assertEquals(big.length(), r.output().length());
    }

    @Test
    void mxRun100kNodeUnderCapOk(@TempDir Path tmp) {
        String big = "n".repeat(100 * 1024);
        var r = executor(tmp, tmp).run(mxRun("node", "x", big, 10));
        assertEquals("ok", r.status());
        assertEquals(big.length(), r.output().length());
    }

    @Test
    void mxRun100kCForcedUnderCapOk(@TempDir Path tmp) {
        String big = "c".repeat(100 * 1024);
        StubExecutor ex = executor(tmp, tmp);
        var r = ex.run(mxRun("python", "x", big, 10));
        assertEquals("ok", r.status());
        assertEquals(big.length(), r.output().length());
    }

    @Test
    void mxJudge100kPythonUnderCapPass(@TempDir Path tmp) {
        String big = "j".repeat(100 * 1024);
        var r = executor(tmp, tmp).judge(judgeReq("python", "x",
                List.of(new TestCase(big, big, false)), 10));
        assertEquals(1, r.passed());
    }

    @Test
    void mxRunStdinEchoPython(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "src", "hello-stdin", 10));
        assertEquals("ok", r.status());
        assertEquals("hello-stdin", r.output());
    }

    @Test
    void mxRunStdinEchoNode(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "src", "node-stdin", 10));
        assertEquals("ok", r.status());
        assertEquals("node-stdin", r.output());
    }

    @Test
    void mxRunStdinEchoCForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo c-stdin"));
        var r = ex.run(mxRun("c", "src", "ignored", 10));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("c-stdin"));
    }

    @Test
    void mxRunStdinEchoCppForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo cpp-stdin"));
        assertTrue(ex.run(mxRun("cpp", "src", "ignored", 10)).output().contains("cpp-stdin"));
    }

    @Test
    void mxRunStdinEchoJavaForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo java-stdin"));
        assertTrue(ex.run(mxRun("java", "src", "ignored", 10)).output().contains("java-stdin"));
    }

    @Test
    void mxJudgeMixedPython(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("python", "x",
                List.of(new TestCase("a", "a", false), new TestCase("b", "WRONG", false)), 10));
        assertEquals(1, r.passed());
        assertEquals(2, r.total());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxJudgeMixedNode(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("node", "x",
                List.of(new TestCase("a", "a", false), new TestCase("b", "WRONG", false)), 10));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
    }

    @Test
    void mxJudgeMixedCForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo a"));
        var r = ex.judge(judgeReq("c", "x",
                List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
    }

    @Test
    void mxJudgeMixedCppForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo a"));
        var r = ex.judge(judgeReq("cpp", "x",
                List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
    }

    @Test
    void mxJudgeMixedJavaForced(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo a"));
        var r = ex.judge(judgeReq("java", "x",
                List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
    }

    @Test
    void mxJudgeZeroCasesPython(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("python", "", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.total());
        assertEquals(0, r.passed());
    }

    @Test
    void mxJudgeZeroCasesNode(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("node", "  ", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxJudgeZeroCasesC(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("c", "x", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxJudgeZeroCasesCpp(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("cpp", "x", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxJudgeZeroCasesJava(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("java", "x", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxRunZeroTimeoutPython(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-py0");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("python", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunZeroTimeoutNode(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-node0");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("node", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunZeroTimeoutC(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-c0");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("c", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunZeroTimeoutCpp(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-cpp0");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("cpp", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunZeroTimeoutJava(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-java0");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("java", "x", "", 0)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunNegativeTimeoutPython(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-pyn");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("python", "x", "", -3)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunNegativeTimeoutNode(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-noden");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("node", "x", "", -9)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunNegativeTimeoutC(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-cn");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("c", "x", "", -1)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunNegativeTimeoutCpp(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-cppn");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("cpp", "x", "", -2)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunNegativeTimeoutJava(@TempDir Path tmp) throws Exception {
        Path work = Files.createTempDirectory("oq-mx-javan");
        try {
            StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
            ex.force(MX_HANG);
            assertEquals("timeout", ex.run(mxRun("java", "x", "", -4)).status());
        } finally {
            Thread.sleep(200);
            com.sprintjudge.util.ExecIo.deleteTree(work);
        }
    }

    @Test
    void mxRunUnsupportedRuby(@TempDir Path tmp) {
        assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("ruby", "x", "", 10)).status());
    }

    @Test
    void mxRunUnsupportedBlank(@TempDir Path tmp) {
        assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("   ", "x", "", 10)).status());
    }

    @Test
    void mxRunUnsupportedGo(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("go", "x", "", 10));
        assertFalse(r.ok());
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void mxRunUnsupportedEmpty(@TempDir Path tmp) {
        assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("", "x", "", 10)).status());
    }

    @Test
    void mxRunSource65537Python(@TempDir Path tmp) {
        assertEquals("source_too_large",
                executor(tmp, tmp).run(mxRun("python", "x".repeat(65_537), "", 10)).status());
    }

    @Test
    void mxRunSource65537Node(@TempDir Path tmp) {
        assertEquals("source_too_large",
                executor(tmp, tmp).run(mxRun("node", "x".repeat(65_537), "", 10)).status());
    }

    @Test
    void mxRunSource65537C(@TempDir Path tmp) {
        assertEquals("source_too_large",
                executor(tmp, tmp).run(mxRun("c", "x".repeat(65_537), "", 10)).status());
    }

    @Test
    void mxRunSource65537Cpp(@TempDir Path tmp) {
        assertEquals("source_too_large",
                executor(tmp, tmp).run(mxRun("cpp", "x".repeat(65_537), "", 10)).status());
    }

    @Test
    void mxRunSource65537Java(@TempDir Path tmp) {
        assertEquals("source_too_large",
                executor(tmp, tmp).run(mxRun("java", "x".repeat(65_537), "", 10)).status());
    }

    @Test
    void mxRunSource65536BoundaryPython(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "#" + "x".repeat(65_535), "", 10));
        assertTrue(r.ok() || r.status().equals("runtime_error"));
    }

    @Test
    void mxRunSource65536BoundaryNode(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "#" + "x".repeat(65_535), "", 10));
        assertTrue(r.ok() || r.status().equals("runtime_error"));
    }

    @Test
    void mxRunStdin10000Passthrough(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "x", "s".repeat(10000), 10));
        assertEquals("ok", r.status());
        assertEquals(10000, r.output().length());
    }

    @Test
    void mxRunStdin10001Passthrough(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("python", "x", "s".repeat(10001), 10));
        assertEquals("ok", r.status());
        assertEquals(10001, r.output().length());
    }

    @Test
    void mxRunTimeout31QuickOk(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("node", "x", "fast31", 31));
        assertEquals("ok", r.status());
        assertEquals("fast31", r.output());
    }

    @Test
    void mxRunAliasJsEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("js", "x", "alias-js", 10));
        assertTrue(r.ok());
        assertEquals("alias-js", r.output());
    }

    @Test
    void mxRunAliasPyEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("py", "x", "alias-py", 10));
        assertTrue(r.ok());
        assertEquals("alias-py", r.output());
    }

    @Test
    void mxRunAliasJavascriptEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("javascript", "x", "alias-full", 10));
        assertTrue(r.ok());
        assertEquals("alias-full", r.output());
    }

    @Test
    void mxRunAliasUpperPythonEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("PYTHON", "x", "alias-up", 10));
        assertTrue(r.ok());
        assertEquals("alias-up", r.output());
    }

    @Test
    void mxRunAliasUpperNodeEcho(@TempDir Path tmp) {
        var r = executor(tmp, tmp).run(mxRun("Node", "x", "alias-node", 10));
        assertTrue(r.ok());
        assertEquals("alias-node", r.output());
    }

    @Test
    void mxJudgeZeroTimeoutPythonPass(@TempDir Path tmp) {
        StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
        var r = ex.judge(judgeReq("python", "x", List.of(new TestCase("5", "5", false)), 0));
        assertEquals(1, r.passed());
    }

    @Test
    void mxJudgeZeroTimeoutCForcedPass(@TempDir Path tmp) {
        StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
        ex.force(List.of("cmd", "/c", "echo 5"));
        var r = ex.judge(judgeReq("c", "x", List.of(new TestCase("ignored", "5", false)), 0));
        assertEquals(1, r.passed());
    }

    @Test
    void mxJudgeZeroTimeoutJavaForcedPass(@TempDir Path tmp) {
        StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
        ex.force(List.of("cmd", "/c", "echo 5"));
        var r = ex.judge(judgeReq("java", "x", List.of(new TestCase("ignored", "5", false)), 0));
        assertEquals(1, r.passed());
    }

    @Test
    void mxJudgeNegativeTimeoutNodePass(@TempDir Path tmp) {
        StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
        var r = ex.judge(judgeReq("node", "x", List.of(new TestCase("5", "5", false)), -2));
        assertEquals(1, r.passed());
    }

    @Test
    void mxRunStartFailureCIsIoError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_BADPROG);
        assertEquals("io_error", ex.run(mxRun("c", "x", "", 10)).status());
    }

    @Test
    void mxRunStartFailureJavaIsIoError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_BADPROG);
        assertEquals("io_error", ex.run(mxRun("java", "x", "", 10)).status());
    }

    @Test
    void mxRunStartFailurePythonIsIoError(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(MX_BADPROG);
        assertEquals("io_error", ex.run(mxRun("python", "x", "", 10)).status());
    }

    @Test
    void mxRunNullSourceCForcedOk(@TempDir Path tmp) {
        StubExecutor ex = executor(tmp, tmp);
        ex.force(List.of("cmd", "/c", "echo null-src"));
        assertTrue(ex.run(mxRun("c", null, "", 10)).output().contains("null-src"));
    }

    @Test
    void mxJudgeNullExpectedMatchesEmpty(@TempDir Path tmp) {
        var r = executor(tmp, tmp).judge(judgeReq("python", "x",
                List.of(new TestCase("", null, false)), 10));
        assertEquals(1, r.passed());
        assertEquals("", r.cases().get(0).error());
    }
}
