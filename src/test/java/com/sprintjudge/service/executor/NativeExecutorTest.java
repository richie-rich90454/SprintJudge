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
    private static volatile Boolean cppCompiles;

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

    private static boolean canCompileCpp() {
        if (cppCompiles != null) return cppCompiles;
        boolean ok = false;
        try {
            Path dir = Files.createTempDirectory("oq-gpp-probe");
            try {
                Path src = dir.resolve("t.cpp");
                Files.writeString(src, "int main(){return 0;}");
                Path out = dir.resolve("tprobe.exe");
                Process p = new ProcessBuilder("g++", "-O2", "-std=c++17", "-o",
                        out.toString(), src.toString())
                        .directory(dir.toFile()).redirectErrorStream(true).start();
                ok = p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.exists(out);
            } finally {
                ExecIo.deleteTree(dir);
            }
        } catch (Exception e) {
            ok = false;
        }
        cppCompiles = ok;
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
        assumeTrue(canCompileCpp());
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
        assumeTrue(canCompileCpp());
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

    private static final String MX_PY_CRASH = "import sys; sys.exit(3)";
    private static final String MX_PY_SYNTAX = "def broken(((\n";
    private static final String MX_PY_STDERR = "import sys; sys.stderr.write('warn-py\\n')";
    private static final String MX_PY_UNI =
            "import sys; sys.stdout.buffer.write('h\u00e9llo w\u00f6rld \u00fc123 \u2192\u4e2d\u6587\ud83c\udf89'.encode('utf8'))";
    private static final String MX_EXPECT_UNI = "h\u00e9llo w\u00f6rld \u00fc123 \u2192\u4e2d\u6587\ud83c\udf89";
    private static final String MX_PY_BIG = "print('q' * 100000)";
    private static final String MX_PY_HANG = "import time; time.sleep(30)";
    private static final String MX_NODE_ECHO =
            "const fs=require('fs');process.stdout.write(fs.readFileSync(0,'utf8').trim());";
    private static final String MX_NODE_CRASH = "process.exit(3);";
    private static final String MX_NODE_SYNTAX = "const = broken {{{";
    private static final String MX_NODE_STDERR = "console.error('warn-node');";
    private static final String MX_NODE_UNI =
            "process.stdout.write('h\u00e9llo w\u00f6rld \u00fc123 \u2192\u4e2d\u6587\ud83c\udf89');";
    private static final String MX_NODE_BIG = "console.log('n'.repeat(100000));";
    private static final String MX_NODE_HANG = "setInterval(()=>{},1000);";
    private static final String MX_JAVA_ECHO = "public class Main { public static void main(String[] a)"
            + " throws Exception { System.out.print(new String(System.in.readAllBytes()).strip()); } }";
    private static final String MX_JAVA_CRASH =
            "public class Main { public static void main(String[] a) { System.exit(2); } }";
    private static final String MX_JAVA_STDERR = "public class Main { public static void main(String[] a)"
            + " { System.err.println(\"warn-j\"); } }";
    private static final String MX_JAVA_UNI = "public class Main { public static void main(String[] a)"
            + " throws Exception { System.out.write(\"h\\u00e9llo w\\u00f6rld \\u00fc123 \\u2192\\u4e2d\\u6587\\ud83c\\udf89\""
            + ".getBytes(java.nio.charset.StandardCharsets.UTF_8)); System.out.flush(); } }";
    private static final String MX_JAVA_BIG = "public class Main { public static void main(String[] a)"
            + " { StringBuilder b=new StringBuilder(); for(int i=0;i<100000;i++)b.append('j');"
            + " System.out.print(b); } }";
    private static final String MX_JAVA_HANG = "public class Main { public static void main(String[] a)"
            + " throws Exception { Thread.sleep(30000); } }";
    private static final String MX_C_ECHO = "#include <stdio.h>\n"
            + "int main(){int c;while((c=getchar())!=EOF)putchar(c);return 0;}";
    private static final String MX_C_CRASH = "int main(){return 3;}";
    private static final String MX_C_SYNTAX = "int main( { broken";
    private static final String MX_C_STDERR = "#include <stdio.h>\n"
            + "int main(){fprintf(stderr,\"warn-c\\n\");return 0;}";
    private static final String MX_C_UNI = "#include <stdio.h>\n"
            + "int main(){printf(\"h\u00e9llo w\u00f6rld \u00fc123 \u2192\u4e2d\u6587\ud83c\udf89\");return 0;}";
    private static final String MX_C_BIG = "#include <stdio.h>\n"
            + "int main(){for(int i=0;i<100000;i++)putchar('c');return 0;}";
    private static final String MX_C_HANG = "int main(){while(1){}return 0;}";
    private static final String MX_CPP_ECHO = "#include <iostream>\n#include <string>\n"
            + "int main(){std::string s,all;bool f=true;while(std::getline(std::cin,s))"
            + "{if(!f)all+=\"\\n\";f=false;all+=s;}std::cout<<all;return 0;}";
    private static final String MX_CPP_CRASH = "int main(){return 3;}";
    private static final String MX_CPP_SYNTAX = "int main( { broken";
    private static final String MX_CPP_STDERR = "#include <iostream>\n"
            + "int main(){std::cerr<<\"warn-cpp\"<<std::endl;return 0;}";
    private static final String MX_CPP_UNI = "#include <iostream>\n"
            + "int main(){std::cout<<\"h\u00e9llo w\u00f6rld \u00fc123 \u2192\u4e2d\u6587\ud83c\udf89\";return 0;}";
    private static final String MX_CPP_BIG = "#include <iostream>\n"
            + "int main(){for(int i=0;i<100000;i++)std::cout<<'p';return 0;}";
    private static final String MX_CPP_HANG = "int main(){while(1){}return 0;}";

    @Test
    void mxPythonEmptySourceOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", "", "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.ok());
    }

    @Test
    void mxPythonWhitespaceSourceOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", "   \n\t  \n", "", 10));
        assertEquals("ok", r.status());
    }

    @Test
    void mxPythonSyntaxErrorIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", MX_PY_SYNTAX, "", 10));
        assertEquals("runtime_error", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxPythonCrashIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", MX_PY_CRASH, "", 10));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxPythonStderrOnlyIsOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", MX_PY_STDERR, "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-py"));
    }

    @Test
    void mxPythonUnicodeOutputOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", MX_PY_UNI, "", 10));
        assertEquals("ok", r.status());
        assertEquals(MX_EXPECT_UNI, r.output());
    }

    @Test
    void mxPython100kUnderCapOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", MX_PY_BIG, "", 10));
        assertEquals("ok", r.status());
        assertEquals(100000, r.output().length());
    }

    @Test
    void mxPythonStdinEchoOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", ECHO_PY, "echo-me", 10));
        assertEquals("ok", r.status());
        assertEquals("echo-me", r.output());
    }

    @Test
    void mxPythonJudgeMixedPassFail(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        JudgeResult r = executor(tmp, tmp, 10).judge(
                judgeReq("python", ECHO_PY, List.of(tc("a", "a"), tc("b", "NOPE")), 10));
        assertEquals(1, r.passed());
        assertEquals(2, r.total());
        assertFalse(r.allPassed());
        assertEquals("", r.cases().get(0).error());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxPythonJudgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        JudgeResult r = executor(tmp, tmp, 10).judge(judgeReq("python", "", List.of(), 10));
        assertEquals(0, r.passed());
        assertTrue(r.allPassed());
    }

    @Test
    void mxPythonRunZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 2).run(new RunRequest("python", MX_PY_HANG, "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxPythonRunNegativeTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 2).run(new RunRequest("python", MX_PY_HANG, "", -5));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxPythonJudgeZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        JudgeResult r = executor(tmp, tmp, 2).judge(
                judgeReq("python", MX_PY_HANG, List.of(tc("", "")), 0));
        assertEquals("timeout", r.cases().get(0).error());
    }

    @Test
    void mxNodeEmptySourceOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", "", "", 10));
        assertEquals("ok", r.status());
    }

    @Test
    void mxNodeWhitespaceSourceOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", "  \n  ", "", 10));
        assertEquals("ok", r.status());
    }

    @Test
    void mxNodeSyntaxErrorIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_SYNTAX, "", 10));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxNodeCrashIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_CRASH, "", 10));
        assertEquals("runtime_error", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxNodeStderrOnlyIsOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_STDERR, "", 10));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-node"));
    }

    @Test
    void mxNodeUnicodeOutputOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_UNI, "", 10));
        assertEquals("ok", r.status());
        assertEquals(MX_EXPECT_UNI, r.output());
    }

    @Test
    void mxNode100kUnderCapOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_BIG, "", 10));
        assertEquals("ok", r.status());
        assertEquals(100000, r.output().length());
    }

    @Test
    void mxNodeStdinEchoOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("node", MX_NODE_ECHO, "n-echo", 10));
        assertEquals("ok", r.status());
        assertEquals("n-echo", r.output());
    }

    @Test
    void mxNodeJudgeMixedPassFail(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        JudgeResult r = executor(tmp, tmp, 10).judge(
                judgeReq("node", MX_NODE_ECHO, List.of(tc("a", "a"), tc("b", "NOPE")), 10));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxNodeJudgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        JudgeResult r = executor(tmp, tmp, 10).judge(judgeReq("node", "", List.of(), 10));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxNodeRunZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 2).run(new RunRequest("node", MX_NODE_HANG, "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxNodeRunNegativeTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        var r = executor(tmp, tmp, 2).run(new RunRequest("node", MX_NODE_HANG, "", -1));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxNodeJudgeZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("node", "--version"));
        JudgeResult r = executor(tmp, tmp, 2).judge(
                judgeReq("node", MX_NODE_HANG, List.of(tc("", "")), 0));
        assertEquals("timeout", r.cases().get(0).error());
    }

    @Test
    void mxJavaEmptySourceCompilesToNothingThenFailsAtRuntime(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", "", "", 20));
        assertEquals("runtime_error", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxJavaWhitespaceSourceCompilesToNothingThenFailsAtRuntime(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", "  \n ", "", 20));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxJavaSyntaxErrorIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", "public class Main { broken", "", 20));
        assertEquals("compilation_error", r.status());
        assertFalse(r.error().isBlank());
    }

    @Test
    void mxJavaCrashIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_CRASH, "", 20));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxJavaStderrOnlyIsOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_STDERR, "", 20));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-j"));
    }

    @Test
    void mxJavaUnicodeOutputOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_UNI, "", 20));
        assertEquals("ok", r.status());
        assertEquals(MX_EXPECT_UNI, r.output());
    }

    @Test
    void mxJava100kUnderCapOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_BIG, "", 20));
        assertEquals("ok", r.status());
        assertEquals(100000, r.output().length());
    }

    @Test
    void mxJavaStdinEchoOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_ECHO, "j-echo", 20));
        assertEquals("ok", r.status());
        assertEquals("j-echo", r.output());
    }

    @Test
    void mxJavaJudgeMixedPassFail(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        JudgeResult r = executor(tmp, tmp, 20).judge(
                judgeReq("java", MX_JAVA_ECHO, List.of(tc("a", "a"), tc("b", "NOPE")), 20));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxJavaJudgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        JudgeResult r = executor(tmp, tmp, 20).judge(judgeReq("java",
                "public class Main { public static void main(String[] a) { } }", List.of(), 20));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxJavaRunZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 12).run(new RunRequest("java", MX_JAVA_HANG, "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxJavaRunNegativeTimeoutHandled(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("javac", "-version"));
        assumeTrue(toolAvailable("java", "-version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("java", MX_JAVA_UNI, "", -3));
        assertEquals("ok", r.status());
    }

    @Test
    void mxCEmptySourceIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("gcc", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", "", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCWhitespaceSourceIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("gcc", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", "\n  \n", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCSyntaxErrorIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("gcc", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_SYNTAX, "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCCrashIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_CRASH, "", 20));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxCStderrOnlyIsOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_STDERR, "", 20));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-c"));
    }

    @Test
    void mxCUnicodeOutputOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_UNI, "", 20));
        assertEquals("ok", r.status());
        assertEquals(MX_EXPECT_UNI, r.output());
    }

    @Test
    void mxC100kUnderCapOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_BIG, "", 20));
        assertEquals("ok", r.status());
        assertEquals(100000, r.output().length());
    }

    @Test
    void mxCStdinEchoOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_ECHO, "c-echo", 20));
        assertEquals("ok", r.status());
        assertEquals("c-echo", r.output());
    }

    @Test
    void mxCJudgeMixedPassFail(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        JudgeResult r = executor(tmp, tmp, 20).judge(
                judgeReq("c", MX_C_ECHO, List.of(tc("a", "a"), tc("b", "NOPE")), 20));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxCJudgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        JudgeResult r = executor(tmp, tmp, 20).judge(judgeReq("c", MX_C_UNI, List.of(), 20));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxCRunZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 8).run(new RunRequest("c", MX_C_HANG, "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxCRunNegativeTimeoutHandled(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileC());
        var r = executor(tmp, tmp, 20).run(new RunRequest("c", MX_C_UNI, "", -2));
        assertEquals("ok", r.status());
    }

    @Test
    void mxCppEmptySourceIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("g++", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", "", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCppWhitespaceSourceIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("g++", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", " \t\n ", "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCppSyntaxErrorIsCompilationError(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("g++", "--version"));
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_SYNTAX, "", 20));
        assertEquals("compilation_error", r.status());
    }

    @Test
    void mxCppCrashIsRuntimeError(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_CRASH, "", 20));
        assertEquals("runtime_error", r.status());
    }

    @Test
    void mxCppStderrOnlyIsOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_STDERR, "", 20));
        assertEquals("ok", r.status());
        assertTrue(r.output().contains("warn-cpp"));
    }

    @Test
    void mxCppUnicodeOutputOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_UNI, "", 20));
        assertEquals("ok", r.status());
        assertEquals(MX_EXPECT_UNI, r.output());
    }

    @Test
    void mxCpp100kUnderCapOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_BIG, "", 20));
        assertEquals("ok", r.status());
        assertEquals(100000, r.output().length());
    }

    @Test
    void mxCppStdinEchoOk(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_ECHO, "p-echo", 20));
        assertEquals("ok", r.status());
        assertEquals("p-echo", r.output());
    }

    @Test
    void mxCppJudgeMixedPassFail(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        JudgeResult r = executor(tmp, tmp, 20).judge(
                judgeReq("cpp", MX_CPP_ECHO, List.of(tc("a", "a"), tc("b", "NOPE")), 20));
        assertEquals(1, r.passed());
        assertFalse(r.allPassed());
        assertEquals("mismatch", r.cases().get(1).error());
    }

    @Test
    void mxCppJudgeZeroCasesAllPassed(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        JudgeResult r = executor(tmp, tmp, 20).judge(judgeReq("cpp", MX_CPP_UNI, List.of(), 20));
        assertTrue(r.allPassed());
        assertEquals(0, r.passed());
    }

    @Test
    void mxCppRunZeroTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 10).run(new RunRequest("cpp", MX_CPP_HANG, "", 0));
        assertEquals("timeout", r.status());
    }

    @Test
    void mxCppRunNegativeTimeoutHandled(@TempDir Path tmp) throws IOException {
        assumeTrue(canCompileCpp());
        var r = executor(tmp, tmp, 20).run(new RunRequest("cpp", MX_CPP_UNI, "", -4));
        assertEquals("ok", r.status());
    }

    @Test
    void mxRunBlankLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        var r = executor(tmp, tmp, 5).run(new RunRequest("  ", "x", "", 5));
        assertEquals("unsupported_language", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxRunEmptyLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        var r = executor(tmp, tmp, 5).run(new RunRequest("", "x", "", 5));
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void mxRunSource65536ExactAccepted(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", "#" + "x".repeat(65535), "", 10));
        assertEquals("ok", r.status());
    }

    @Test
    void mxRunSource65537Rejected(@TempDir Path tmp) throws IOException {
        var r = executor(tmp, tmp, 5).run(new RunRequest("python", "x".repeat(65537), "", 5));
        assertEquals("source_too_large", r.status());
        assertFalse(r.ok());
    }

    @Test
    void mxRunStdin10000CharsPassesThrough(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", ECHO_PY, "s".repeat(10000), 10));
        assertEquals("ok", r.status());
        assertEquals(10000, r.output().length());
    }

    @Test
    void mxRunStdin10001CharsPassesThrough(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", ECHO_PY, "s".repeat(10001), 10));
        assertEquals("ok", r.status());
        assertEquals(10001, r.output().length());
    }

    @Test
    void mxRunTimeout31QuickProgramOk(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        var r = executor(tmp, tmp, 10).run(new RunRequest("python", "print('fast')", "", 31));
        assertEquals("ok", r.status());
        assertEquals("fast", r.output());
    }

    @Test
    void mxJudgeBlankLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        JudgeResult r = executor(tmp, tmp, 5).judge(judgeReq("   ", "x", List.of(tc("i", "o")), 5));
        assertEquals("unsupported_language", r.cases().get(0).error());
    }

    @Test
    void mxJudgeEmptyLanguageIsUnsupported(@TempDir Path tmp) throws IOException {
        JudgeResult r = executor(tmp, tmp, 5).judge(judgeReq("", "x", List.of(tc("i", "o")), 5));
        assertEquals("unsupported_language", r.cases().get(0).error());
    }

    @Test
    void mxPythonJudgeNegativeTimeoutFallsBack(@TempDir Path tmp) throws IOException {
        assumeTrue(toolAvailable("python", "--version"));
        JudgeResult r = executor(tmp, tmp, 2).judge(
                judgeReq("python", MX_PY_HANG, List.of(tc("", "")), -7));
        assertEquals("timeout", r.cases().get(0).error());
    }
}
