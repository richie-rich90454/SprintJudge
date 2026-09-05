package com.sprintjudge.service.executor;

import com.sprintjudge.util.ExecIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeExecutorTest {

    private static NativeExecutor executor(Path work, Path cacheDir, int timeoutSec) throws IOException {
        CompileArtifactCache cache = new CompileArtifactCache(cacheDir.toString(), 16, 16);
        NativeExecutor ex = new NativeExecutor(cache);
        ReflectionTestUtils.setField(ex, "workDirBase", work.toString());
        ReflectionTestUtils.setField(ex, "defaultTimeoutSec", timeoutSec);
        return ex;
    }

    private static volatile Boolean cCompiles;

    private static boolean canCompileC() {
        if (cCompiles != null) return cCompiles;
        boolean ok = false;
        try {
            Path dir = Files.createTempDirectory("oq-gcc-probe");
            try {
                Path src = dir.resolve("t.c");
                Files.writeString(src, "int main(){return 0;}");
                Path out = dir.resolve("tprobe.exe");
                Process p = new ProcessBuilder("gcc", "-O2", "-o",
                        out.toString(), src.toString())
                        .directory(dir.toFile()).redirectErrorStream(true).start();
                ok = p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.exists(out);
            } finally {
                ExecIo.deleteTree(dir);
            }
        } catch (Exception e) {
            ok = false;
        }
        cCompiles = ok;
        return ok;
    }

    private static boolean toolAvailable(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            p.destroyForcibly();
            return finished;
        } catch (Exception e) {
            return false;
        }
    }

    private static JudgeRequest judgeReq(String language, String source, List<TestCase> tcs, int timeout) {
        return new JudgeRequest(language, source, tcs, timeout, 0);
    }

    private static TestCase tc(String input, String expected) {
        return new TestCase(input, expected, false);
    }

    private static final String ECHO_PY = "import sys; print(sys.stdin.read().strip())";

    @Test
    void supportsCanonicalAndAliases() throws IOException {
        NativeExecutor ex = executor(Path.of("wd"), Files.createTempDirectory("cache"), 5);
        assertTrue(ex.supports("c"));
        assertTrue(ex.supports("cpp"));
        assertTrue(ex.supports("java"));
        assertTrue(ex.supports("node"));
        assertTrue(ex.supports("python"));
        assertTrue(ex.supports("javascript"));
        assertTrue(ex.supports("js"));
        assertTrue(ex.supports("py"));
        assertTrue(ex.supports("PYTHON"));
    }

    @Test
    void supportsRejectsUnknownNullAndEmpty(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        assertFalse(ex.supports("ruby"));
        assertFalse(ex.supports(null));
        assertFalse(ex.supports(""));
        assertFalse(ex.supports(" python "));
    }

    @Test
    void judgeUnsupportedLanguage(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        JudgeResult r = ex.judge(judgeReq("ruby", "x", List.of(tc("in", "out")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals(1, r.cases().size());
        assertEquals("unsupported_language", r.cases().get(0).error());
    }

    @Test
    void judgeNullLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        JudgeResult r = ex.judge(judgeReq(null, "x", List.of(tc("in", "out")), 10));
        assertEquals("unsupported_language", r.cases().get(0).error());
    }

    @Test
    void judgeRunDirFailureReturnsEmpty(@TempDir Path tmp) throws IOException {
        Path blocker = tmp.resolve("blocker");
        Files.createFile(blocker);
        NativeExecutor ex = executor(blocker, tmp, 5);
        JudgeResult r = ex.judge(judgeReq("python", "x", List.of(tc("in", "out")), 10));
        assertEquals(0, r.passed());
        assertTrue(r.cases().isEmpty());
    }

    @Test
    void judgePythonPass(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", ECHO_PY, List.of(tc("hello", "hello")), 10));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
        assertEquals("", r.cases().get(0).error());
    }

    @Test
    void judgePythonAliasJsRunsNode(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        String src = "const fs=require('fs');process.stdout.write(fs.readFileSync(0,'utf8').trim());";
        JudgeResult r = ex.judge(judgeReq("js", src, List.of(tc("abc", "abc")), 10));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void judgePythonMismatch(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", ECHO_PY, List.of(tc("a", "b")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(0).error());
    }

    @Test
    void judgePythonNullSourceAndNullExpectedPass(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", null, List.of(tc(null, null)), 10));
        assertEquals(1, r.passed());
        assertTrue(r.cases().get(0).passed());
    }

    @Test
    void judgePythonExpectedWhitespaceIsStripped(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", ECHO_PY, List.of(tc("hi", "  hi  ")), 10));
        assertEquals(1, r.passed());
    }

    @Test
    void judgePythonTimeout(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(
                judgeReq("python", "import time; time.sleep(30)", List.of(tc("", "")), 1));
        assertEquals(0, r.passed());
        assertEquals("timeout", r.cases().get(0).error());
    }

    @Test
    void judgePythonStdoutCap(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(
                judgeReq("python", "print('x' * 3000000)", List.of(tc("", "")), 10));
        assertEquals("stdout_exceeded_1MB", r.cases().get(0).error());
    }

    @Test
    void judgePythonMixedPassAndFail(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", ECHO_PY,
                List.of(tc("a", "a"), tc("b", "WRONG")), 10));
        assertEquals(1, r.passed());
        assertEquals(2, r.total());
        assertFalse(r.allPassed());
    }

    @Test
    void judgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        JudgeResult r = ex.judge(judgeReq("python", "print(1)", List.of(), 10));
        assertEquals(0, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void judgeFallsBackToDefaultTimeout(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 1);
        JudgeResult r = ex.judge(
                judgeReq("python", "import time; time.sleep(30)", List.of(tc("", "")), 0));
        assertEquals("timeout", r.cases().get(0).error());
    }

    @Test
    void judgeJavaCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        JudgeResult r = ex.judge(judgeReq("java", "not java at all {{{", List.of(tc("in", "out")), 20));
        assertEquals(0, r.passed());
        assertEquals("compilation_error", r.cases().get(0).error());
    }

    @Test
    void judgeCCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("gcc", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        JudgeResult r = ex.judge(judgeReq("c", "int main( { broken", List.of(tc("in", "out")), 20));
        assertEquals("compilation_error", r.cases().get(0).error());
    }

    @Test
    void judgeCppCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("g++", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        JudgeResult r = ex.judge(judgeReq("cpp", "int main( { broken", List.of(tc("in", "out")), 20));
        assertEquals("compilation_error", r.cases().get(0).error());
    }

    @Test
    void judgeJavaSuccess(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main { public static void main(String[] a) { System.out.println(\"hi\"); } }";
        JudgeResult r = ex.judge(judgeReq("java", src, List.of(tc("", "hi")), 20));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void judgeCompileErrorWithZeroCases(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        JudgeResult r = ex.judge(judgeReq("java", "broken {{{", List.of(), 20));
        assertEquals(0, r.passed());
        assertTrue(r.cases().isEmpty());
        assertFalse(r.allPassed());
    }

    @Test
    void judgePythonSlowDripIsTooBig(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 30);
        String src = "import time\nfor i in range(40):\n print('x' * 100000, flush=True)\n time.sleep(0.1)\n";
        JudgeResult r = ex.judge(judgeReq("python", src, List.of(tc("", "")), 30));
        assertEquals("stdout_exceeded_1MB", r.cases().get(0).error());
    }

    @Test
    void judgeInterruptedCompileEmptiesResult(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        try (var mocked = org.mockito.Mockito.mockStatic(com.sprintjudge.util.ExecIo.class)) {
            mocked.when(() -> ExecIo.awaitBounded(
                    org.mockito.ArgumentMatchers.any(Process.class),
                    org.mockito.ArgumentMatchers.any(Path.class),
                    org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(inv -> { throw new InterruptedException("stop"); });
            mocked.when(() -> ExecIo.killAndReap(
                    org.mockito.ArgumentMatchers.any(Process.class)))
                    .thenCallRealMethod();
            mocked.when(() -> ExecIo.deleteTree(
                    org.mockito.ArgumentMatchers.any(Path.class)))
                    .thenCallRealMethod();
            JudgeResult r = ex.judge(judgeReq("python", ECHO_PY, List.of(tc("a", "a")), 10));
            assertTrue(r.cases().isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void runInterruptedEmptiesResult(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        try (var mocked = org.mockito.Mockito.mockStatic(com.sprintjudge.util.ExecIo.class)) {
            mocked.when(() -> ExecIo.awaitBounded(
                    org.mockito.ArgumentMatchers.any(Process.class),
                    org.mockito.ArgumentMatchers.any(Path.class),
                    org.mockito.ArgumentMatchers.anyLong()))
                    .thenAnswer(inv -> { throw new InterruptedException("stop"); });
            mocked.when(() -> ExecIo.killAndReap(
                    org.mockito.ArgumentMatchers.any(Process.class)))
                    .thenCallRealMethod();
            mocked.when(() -> ExecIo.deleteTree(
                    org.mockito.ArgumentMatchers.any(Path.class)))
                    .thenCallRealMethod();
            var r = ex.run(new RunRequest("python", "print(1)", "", 10));
            assertEquals("io_error", r.status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void runCppCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("g++", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        var r = ex.run(new RunRequest("cpp", "int main( { broken", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void runCCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("gcc", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        var r = ex.run(new RunRequest("c", "int main( { broken", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void runNodeOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("node", "console.log('node-ok');", "", 10));
        assertTrue(r.ok());
        assertEquals("node-ok", r.output());
    }

    @Test
    void judgeJavaLongCompileErrorTruncates(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main {\n" + "  this is not java @#$ line\n".repeat(300) + "}\n";
        JudgeResult r = ex.judge(judgeReq("java", src, List.of(tc("", "")), 20));
        assertEquals("compilation_error", r.cases().get(0).error());
    }

    @Test
    void judgePreInterruptedCompile(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main { public static void main(String[] a) { System.out.println(1); } }";
        Thread.currentThread().interrupt();
        try {
            JudgeResult r = ex.judge(judgeReq("java", src, List.of(tc("", "1")), 20));
            if (r.cases().isEmpty()) {
                assertTrue(Thread.currentThread().isInterrupted());
            } else {
                String err = r.cases().get(0).error();
                assertTrue(err.equals("compilation_error") || err.equals("timeout"), err);
            }
        } finally {
            Thread.interrupted();
            awaitRunDirsGone(tmp);
        }
    }

    @Test
    void runPreInterruptedCompile(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main { public static void main(String[] a) { System.out.println(1); } }";
        Thread.currentThread().interrupt();
        try {
            var r = ex.run(new RunRequest("java", src, "", 20));
            assertTrue(r.status().equals("io_error") || r.status().equals("compilation_error"), r.status());
        } finally {
            Thread.interrupted();
            awaitRunDirsGone(tmp);
        }
    }

    private static void awaitRunDirsGone(Path tmp) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        try {
            while (System.nanoTime() < deadline) {
                boolean gone = true;
                try (var stream = Files.list(tmp)) {
                    for (Path p : stream.toList()) {
                        if (p.getFileName().toString().startsWith("run-")) {
                            ExecIo.deleteTree(p);
                            if (Files.exists(p)) gone = false;
                        }
                    }
                }
                if (gone) return;
                Thread.sleep(250);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // best effort only; @TempDir cleanup follows
        }
    }

    @Test
    void runJavaSuccess(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main { public static void main(String[] a) { System.out.println(\"ok-java\"); } }";
        var r = ex.run(new RunRequest("java", src, "", 20));
        assertTrue(r.ok());
        assertEquals("ok-java", r.output());
    }

    @Test
    void runUsesDefaultTimeout(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 1);
        var r = ex.run(new RunRequest("python", "import time; time.sleep(30)", "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void runPythonSlowDripIsTooBig(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 30);
        String src = "import time\nfor i in range(40):\n print('y' * 100000, flush=True)\n time.sleep(0.1)\n";
        var r = ex.run(new RunRequest("python", src, "", 30));
        assertEquals("stdout_exceeded_1MB", r.status());
    }

    @Test
    void judgeCCacheHitOnResubmit(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        Path cacheDir = tmp.resolve("cache");
        Files.createDirectories(cacheDir);
        CompileArtifactCache cache = new CompileArtifactCache(cacheDir.toString(), 16, 16);
        NativeExecutor ex = new NativeExecutor(cache);
        ReflectionTestUtils.setField(ex, "workDirBase", tmp.toString());
        ReflectionTestUtils.setField(ex, "defaultTimeoutSec", 20);
        String src = "int main(){return 0;}";
        JudgeResult first = ex.judge(judgeReq("c", src, List.of(tc("", "")), 20));
        assertEquals(1, first.passed());
        JudgeResult second = ex.judge(judgeReq("c", src, List.of(tc("", "")), 20));
        assertEquals(1, second.passed());
        assertTrue(cache.hits() >= 1);
    }

    @Test
    void judgeCCacheVanishRecompiles(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        Path cacheDir = tmp.resolve("cache");
        Files.createDirectories(cacheDir);
        CompileArtifactCache cache = new CompileArtifactCache(cacheDir.toString(), 16, 16);
        NativeExecutor ex = new NativeExecutor(cache);
        ReflectionTestUtils.setField(ex, "workDirBase", tmp.toString());
        ReflectionTestUtils.setField(ex, "defaultTimeoutSec", 20);
        String src = "int main(){return 0;}";
        assertEquals(1, ex.judge(judgeReq("c", src, List.of(tc("", "")), 20)).passed());
        Path cached = cache.get(CompileArtifactCache.keyFor("c", src)).orElseThrow();
        Files.deleteIfExists(cached);
        assertEquals(1, ex.judge(judgeReq("c", src, List.of(tc("", "")), 20)).passed());
    }

    @Test
    void judgeNodePass(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        String src = "console.log('hi');";
        JudgeResult r = ex.judge(judgeReq("node", src, List.of(tc("", "hi")), 10));
        assertEquals(1, r.passed());
    }

    @Test
    void runUnsupportedLanguage(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        var r = ex.run(new RunRequest("ruby", "x", "", 5));
        assertFalse(r.ok());
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void runNullLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        var r = ex.run(new RunRequest(null, "x", "", 5));
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void runOversizedSource(@TempDir Path tmp) throws IOException {
        NativeExecutor ex = executor(tmp, tmp, 5);
        var r = ex.run(new RunRequest("python", "x".repeat(65_537), "", 5));
        assertEquals("source_too_large", r.status());
    }

    @Test
    void runBoundarySizeIsAccepted(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        String src = "#" + "x".repeat(65_535);
        var r = ex.run(new RunRequest("python", src, "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.ok());
    }

    @Test
    void runDirFailureIsIoError(@TempDir Path tmp) throws IOException {
        Path blocker = tmp.resolve("blocker");
        Files.createFile(blocker);
        NativeExecutor ex = executor(blocker, tmp, 5);
        var r = ex.run(new RunRequest("python", "print(1)", "", 5));
        assertEquals("io_error", r.status());
    }

    @Test
    void runPythonOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", "print('hello')", "", 10));
        assertTrue(r.ok());
        assertEquals("hello", r.output());
        assertEquals("ok", r.status());
    }

    @Test
    void runPythonWithStdin(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", ECHO_PY, "stdin-hi", 10));
        assertTrue(r.ok());
        assertEquals("stdin-hi", r.output());
    }

    @Test
    void runPythonRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", "import sys; sys.exit(1)", "", 10));
        assertFalse(r.ok());
        assertEquals("runtime_error", r.status());
    }

    @Test
    void runPythonTimeout(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", "import time; time.sleep(30)", "", 1));
        assertEquals("timeout", r.status());
    }

    @Test
    void runPythonStdoutCap(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", "print('y' * 3000000)", "", 10));
        assertEquals("stdout_exceeded_1MB", r.status());
    }

    @Test
    void runNullSourceIsEmptyProgram(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", null, "", 10));
        assertTrue(r.ok());
        assertEquals("ok", r.status());
    }

    @Test
    void runNullStdinNeedsNoInputFile(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 10);
        var r = ex.run(new RunRequest("python", "print('nostdin')", null, 10));
        assertEquals("nostdin", r.output());
    }

    @Test
    void runJavaCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        var r = ex.run(new RunRequest("java", "broken {{{", "", 20));
        assertEquals("compilation_error", r.status());
        assertFalse(r.error().isBlank());
    }

    @Test
    void compileCommandNullForInterpreted(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("compileCommand",
                String.class, Path.class, Path.class);
        m.setAccessible(true);
        assertNull(m.invoke(ex, "python", tmp, tmp));
        assertNull(m.invoke(ex, "node", tmp, tmp));
        assertNotNull(m.invoke(ex, "c", tmp, tmp));
        assertNotNull(m.invoke(ex, "cpp", tmp, tmp));
        assertNotNull(m.invoke(ex, "java", tmp, tmp));
    }

    @Test
    void cacheableOnlyForCAndCpp(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("cacheable", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(ex, "c"));
        assertTrue((boolean) m.invoke(ex, "cpp"));
        assertFalse((boolean) m.invoke(ex, "java"));
        assertFalse((boolean) m.invoke(ex, "python"));
        assertFalse((boolean) m.invoke(ex, "node"));
    }

    @Test
    void mainClassStripsJavaExtension(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("mainClass", Path.class);
        m.setAccessible(true);
        assertEquals("Main", m.invoke(ex, Path.of("Main.java")));
        assertEquals("Foo", m.invoke(ex, Path.of("/some/dir/Foo.java")));
    }

    @Test
    void runCommandShapes(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("runCommand",
                String.class, Path.class, Path.class);
        m.setAccessible(true);
        Path src = tmp.resolve("solution.py");
        List<String> py = (List<String>) m.invoke(ex, "python", src, tmp);
        assertEquals(List.of("python", src.toString()), py);
        List<String> node = (List<String>) m.invoke(ex, "node", src, tmp);
        assertEquals("node", node.get(0));
        List<String> java = (List<String>) m.invoke(ex, "java", tmp.resolve("Main.java"), tmp);
        assertEquals("java", java.get(0));
        assertTrue(java.contains("Main"));
        List<String> c = (List<String>) m.invoke(ex, "c", src, tmp);
        assertEquals(1, c.size());
        List<String> cpp = (List<String>) m.invoke(ex, "cpp", src, tmp);
        assertEquals(1, cpp.size());
    }

    @Test
    void binaryNameIsOsSpecific(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("binary", Path.class);
        m.setAccessible(true);
        Path bin = (Path) m.invoke(ex, tmp);
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        assertEquals(win ? "program.exe" : "program", bin.getFileName().toString());
    }

    @Test
    void truncateShortAndLong(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("truncate", String.class);
        m.setAccessible(true);
        assertEquals("abc", m.invoke(ex, "abc"));
        String cut = (String) m.invoke(ex, "x".repeat(3000));
        assertEquals(2001, cut.length());
        assertTrue(cut.endsWith("…"));
    }

    @Test
    void runToCompletionSuccess(@TempDir Path tmp) throws Exception {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("runToCompletion",
                List.class, Path.class, int.class);
        m.setAccessible(true);
        assertNull(m.invoke(ex, List.of("python", "-c", "print('ok')"), tmp, 20));
    }

    @Test
    void runToCompletionFailureReturnsOutput(@TempDir Path tmp) throws Exception {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("runToCompletion",
                List.class, Path.class, int.class);
        m.setAccessible(true);
        String err = (String) m.invoke(ex,
                List.of("python", "-c", "import sys; print('bad'); sys.exit(1)"), tmp, 20);
        assertTrue(err.contains("bad"), err);
    }

    @Test
    void runToCompletionTimeout(@TempDir Path tmp) throws Exception {
        assumeTrue(toolAvailable("python", "--version"));
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("runToCompletion",
                List.class, Path.class, int.class);
        m.setAccessible(true);
        assertEquals("compile_timeout", m.invoke(ex,
                List.of("python", "-c", "import time; time.sleep(30)"), tmp, 1));
    }

    @Test
    void judgeCacheHitCopiesBinaryWithoutCompiling(@TempDir Path tmp) throws IOException {
        Path fakeBin = tmp.resolve("fake-bin");
        Files.write(fakeBin, new byte[]{1, 2, 3});
        CompileArtifactCache cache = mock(CompileArtifactCache.class);
        when(cache.get(CompileArtifactCache.keyFor("c", "int x;"))).thenReturn(java.util.Optional.of(fakeBin));
        NativeExecutor ex = new NativeExecutor(cache);
        ReflectionTestUtils.setField(ex, "workDirBase", tmp.toString());
        ReflectionTestUtils.setField(ex, "defaultTimeoutSec", 10);
        var r = ex.judge(judgeReq("c", "int x;", List.of(tc("", "never-this")), 10));
        assertEquals(0, r.passed());
        assertFalse(r.allPassed());
    }

    @Test
    void binaryNameFollowsOsProperty(@TempDir Path tmp) throws Exception {
        NativeExecutor ex = executor(tmp, tmp, 5);
        Method m = NativeExecutor.class.getDeclaredMethod("binary", Path.class);
        m.setAccessible(true);
        String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            assertEquals("program", ((Path) m.invoke(ex, tmp)).getFileName().toString());
        } finally {
            if (original != null) System.setProperty("os.name", original);
        }
        assertTrue(((Path) m.invoke(ex, tmp)).getFileName().toString().endsWith(".exe")
                == System.getProperty("os.name", "").toLowerCase().contains("win"));
    }

    @Test
    void maybeCachePutsOnCleanCompile(@TempDir Path tmp) throws IOException {
        Path bin = tmp.resolve("program.exe");
        Files.write(bin, new byte[]{9});
        CompileArtifactCache cache = mock(CompileArtifactCache.class);
        NativeExecutor.maybeCache(cache, "c", "int x;", tmp, null);
        verify(cache).put(CompileArtifactCache.keyFor("c", "int x;"), bin);
    }

    @Test
    void maybeCacheSkipsOnCompileError(@TempDir Path tmp) {
        CompileArtifactCache cache = mock(CompileArtifactCache.class);
        NativeExecutor.maybeCache(cache, "c", "int x;", tmp, "compilation_error");
        verify(cache, org.mockito.Mockito.never()).put(any(), any());
    }

    @Test
    void judgeVanishingCacheEntryRecompiles(@TempDir Path tmp) throws IOException {
        Path ghost = tmp.resolve("ghost-bin");
        Files.write(ghost, new byte[]{1});
        CompileArtifactCache cache = mock(CompileArtifactCache.class);
        when(cache.get(CompileArtifactCache.keyFor("c", "int x;")))
                .thenAnswer(inv -> {
                    Files.deleteIfExists(ghost);
                    return java.util.Optional.of(ghost);
                });
        NativeExecutor ex = new NativeExecutor(cache);
        ReflectionTestUtils.setField(ex, "workDirBase", tmp.toString());
        ReflectionTestUtils.setField(ex, "defaultTimeoutSec", 10);
        var r = ex.judge(judgeReq("c", "int x;", List.of(tc("", "never-this")), 10));
        assertEquals(0, r.passed());
    }

    @Test
    void judgeJavaSuccessCachesBinary(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        String src = "public class Main { public static void main(String[] a) { System.out.print(\"hi\"); } }";
        var r = ex.judge(judgeReq("java", src, List.of(tc("", "hi")), 20));
        assertEquals(1, r.passed());
        assertTrue(r.allPassed());
    }
    @Test
    void compileCapReportedWhenSpewExceeds(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        NativeExecutor ex = executor(tmp, tmp, 20);
        try (var mocked = org.mockito.Mockito.mockStatic(ExecIo.class)) {
            mocked.when(() -> ExecIo.readCapped(any(Process.class))).thenReturn(null);
            mocked.when(() -> ExecIo.awaitBounded(any(), any(), anyLong()))
                    .thenCallRealMethod();
            mocked.when(() -> ExecIo.readCappedFile(any(Path.class)))
                    .thenCallRealMethod();
            var r = ex.run(new RunRequest("java", "broken {{{", "", 20));
            assertEquals("compilation_error", r.status());
            assertEquals("stdout_exceeded_1MB", r.error());
        }
    }
}
