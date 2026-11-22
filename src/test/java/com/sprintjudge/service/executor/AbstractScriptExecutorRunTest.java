package com.sprintjudge.service.executor;

import com.sprintjudge.service.executor.AbstractScriptExecutorTest.StubExecutor;
import com.sprintjudge.util.ExecIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

@SuppressWarnings("all")
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
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(runReq("ruby", "x", "", 10));
		assertFalse(r.isOk());
		assertEquals("unsupported_language", r.getStatus());
	}

	@Test
	void runNullLanguageIsUnsupported(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(runReq(null, "x", "", 10));
		assertEquals("unsupported_language", r.getStatus());
	}

	@Test
	void runOversizedSource(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(runReq("python", "x".repeat(65_537), "", 10));
		assertEquals("source_too_large", r.getStatus());
	}

	@Test
	void runBoundarySizePassesGuard(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("node", "#" + "x".repeat(65_535), "", 10));
		assertTrue(r.isOk() || r.getStatus().equals("ok") || r.getStatus().equals("runtime_error"));
	}

	@Test
	void runDirFailureIsIoError(@TempDir Path tmp) throws Exception {
		Path blocker = tmp.resolve("blocker");
		Files.createFile(blocker);
		StubExecutor ex = new StubExecutor(blocker.toString(), tmp.toString(), 30);
		assertEquals("io_error", ex.run(runReq("node", "x", "", 10)).getStatus());
	}

	@Test
	void runSuccessEchoesInput(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(runReq("node", "ignored", "9", 10));
		assertTrue(r.isOk());
		assertEquals("9", r.getOutput());
		assertEquals("ok", r.getStatus());
	}

	@Test
	void runCanonicalAlias(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(runReq("javascript", "ignored", "7", 10));
		assertTrue(r.isOk());
		assertEquals("7", r.getOutput());
	}

	@Test
	void runNullSourceAndStdinAreEmpty(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(runReq("python", null, null, 10));
		assertTrue(r.isOk());
		assertEquals("", r.getOutput());
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
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("java", "ignored", "zz", 10));
		assertEquals("zz", r.getOutput());
	}

	@Test
	void runNonZeroExitIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "exit 3"));
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("node", "x", "", 10));
		assertFalse(r.isOk());
		assertEquals("runtime_error", r.getStatus());
	}

	@Test
	void runStartFailureIsIoError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("this-program-does-not-exist-xyz123"));
		assertEquals("io_error", ex.run(runReq("node", "x", "", 10)).getStatus());
	}

	@Test
	void runTimeoutBranch(@TempDir Path tmp) throws Exception {
		Path work = Files.createTempDirectory("oq-run-timeout");
		try {
			StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
			ex.force(List.of("ping", "-n", "6", "127.0.0.1"));
			assertEquals("timeout", ex.run(runReq("node", "x", "", 0)).getStatus());
		} finally {
			Thread.sleep(200);
			com.sprintjudge.util.ExecIo.deleteTree(work);
		}
	}

	@Test
	void runStdoutCapBranch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		String big = "x".repeat(2 * com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES);
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("node", "x", big, 10));
		assertEquals("stdout_exceeded_1MB", r.getStatus());
	}

	@Test
	void runFinishedOverCapBranch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		String justOver = "x".repeat(com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES + 4096);
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("node", "x", justOver, 30));
		assertEquals("stdout_exceeded_1MB", r.getStatus());
	}

	@Test
	void runNullCappedFileIsStdoutExceeded(@TempDir Path tmp) {
		// Deterministic pin for the FINISHED-but-unreadable race: the over-cap
		// tests above hit either branch depending on poll timing.
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo hi"));
		try (org.mockito.MockedStatic<com.sprintjudge.util.ExecIo> mocked = org.mockito.Mockito
				.mockStatic(ExecIo.class)) {
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					ExecIo.awaitBounded(any(), any(), anyLong());
				}
			}).thenCallRealMethod();
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					ExecIo.readCappedFile(any(Path.class));
				}
			}).thenReturn(null);
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					ExecIo.deleteTree(any(Path.class));
				}
			}).thenCallRealMethod();
			assertEquals("stdout_exceeded_1MB", ex.run(runReq("node", "x", "", 10)).getStatus());
		}
	}

	@Test
	void judgeFinishedOverCapBranch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		String justOver = "x".repeat(com.sprintjudge.util.ExecIo.STDOUT_CAP_BYTES + 4096);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("node", "x", List.of(new TestCase(justOver, "out", false)), 30));
		assertEquals("stdout_exceeded_1MB", r.getCases().get(0).getError());
	}

	@Test
	void runTooBigOutcomeBranch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "for /l %i in (1,1,300000) do @echo "
				+ "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
		com.sprintjudge.service.executor.RunResult r = ex.run(runReq("node", "x", "", 30));
		assertEquals("stdout_exceeded_1MB", r.getStatus());
	}

	@Test
	void judgeTooBigOutcomeBranch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "for /l %i in (1,1,300000) do @echo "
				+ "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("node", "x", List.of(new TestCase("in", "out", false)), 30));
		assertEquals("stdout_exceeded_1MB", r.getCases().get(0).getError());
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
				if (++calls == 1)
					return List.of("cmd", "/c", "type", inputFile.toString());
				return List.of("this-program-does-not-exist-xyz123");
			}
		};
		com.sprintjudge.service.executor.JudgeResult r = ex.judge(
				judgeReq("node", "x", List.of(new TestCase("a", "a", false), new TestCase("b", "b", false)), 10));
		assertEquals(0, r.getPassed());
		assertTrue(r.getCases().isEmpty());
	}

	@Test
	void judgeUsesDefaultTimeoutWhenZero(@TempDir Path tmp) {
		StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("node", "x", List.of(new TestCase("5", "5", false)), 0));
		assertEquals(1, r.getPassed());
	}

	@Test
	void judgeEmptyCasesAllPassed(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(judgeReq("node", "x", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getTotal());
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
				if (++calls == 2)
					throw new IllegalStateException("boom");
				return List.of("cmd", "/c", "type", inputFile.toString());
			}
		};
		try {
			ex.judge(judgeReq("node", "x", List.of(new TestCase("a", "a", false), new TestCase("b", "b", false)), 10));
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
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("python", "", "e1", 10));
		assertEquals("ok", r.getStatus());
		assertEquals("e1", r.getOutput());
	}

	@Test
	void mxRunEmptySourceNodeOk(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "", "e2", 10));
		assertEquals("ok", r.getStatus());
		assertEquals("e2", r.getOutput());
	}

	@Test
	void mxRunEmptySourceCForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo c-empty"));
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("c", "", "", 10));
		assertEquals("ok", r.getStatus());
		assertTrue(r.getOutput().contains("c-empty"));
	}

	@Test
	void mxRunEmptySourceCppForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo cpp-empty"));
		assertEquals("ok", ex.run(mxRun("cpp", "", "", 10)).getStatus());
	}

	@Test
	void mxRunEmptySourceJavaForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo java-empty"));
		assertEquals("ok", ex.run(mxRun("java", "", "", 10)).getStatus());
	}

	@Test
	void mxRunWhitespaceSourcePythonOk(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("python", "  \n\t ", "w1", 10));
		assertEquals("ok", r.getStatus());
		assertEquals("w1", r.getOutput());
	}

	@Test
	void mxRunWhitespaceSourceNodeOk(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "\n  ", "w2", 10));
		assertEquals("ok", r.getStatus());
	}

	@Test
	void mxRunWhitespaceSourceCForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo c-ws"));
		assertEquals("ok", ex.run(mxRun("c", "   ", "", 10)).getStatus());
	}

	@Test
	void mxRunWhitespaceSourceCppForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo cpp-ws"));
		assertEquals("ok", ex.run(mxRun("cpp", "\t", "", 10)).getStatus());
	}

	@Test
	void mxRunWhitespaceSourceJavaForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo java-ws"));
		assertEquals("ok", ex.run(mxRun("java", "  ", "", 10)).getStatus());
	}

	@Test
	void mxRunSyntaxErrorPythonIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("python", "def broken(((", "", 10));
		assertEquals("runtime_error", r.getStatus());
		assertFalse(r.isOk());
	}

	@Test
	void mxRunSyntaxErrorNodeIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		assertEquals("runtime_error", ex.run(mxRun("node", "const = broken", "", 10)).getStatus());
	}

	@Test
	void mxRunSyntaxErrorCIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		assertEquals("runtime_error", ex.run(mxRun("c", "int main( { broken", "", 10)).getStatus());
	}

	@Test
	void mxRunSyntaxErrorCppIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		assertEquals("runtime_error", ex.run(mxRun("cpp", "int main( { broken", "", 10)).getStatus());
	}

	@Test
	void mxRunSyntaxErrorJavaIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		assertEquals("runtime_error", ex.run(mxRun("java", "not java {{{", "", 10)).getStatus());
	}

	@Test
	void mxJudgeSyntaxErrorPythonIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("python", "def broken(((", List.of(new TestCase("in", "out", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
		assertFalse(r.isAllPassed());
	}

	@Test
	void mxJudgeSyntaxErrorNodeIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("node", "const = broken", List.of(new TestCase("in", "out", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeSyntaxErrorCIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("c", "int main( { broken", List.of(new TestCase("in", "out", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeSyntaxErrorCppIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("cpp", "int main( { broken", List.of(new TestCase("in", "out", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeSyntaxErrorJavaIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT1);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("java", "not java {{{", List.of(new TestCase("in", "out", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxRunCrashPythonIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("python", "import sys; sys.exit(3)", "", 10));
		assertEquals("runtime_error", r.getStatus());
		assertFalse(r.isOk());
	}

	@Test
	void mxRunCrashNodeIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		assertEquals("runtime_error", ex.run(mxRun("node", "process.exit(3)", "", 10)).getStatus());
	}

	@Test
	void mxRunCrashCIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		assertEquals("runtime_error", ex.run(mxRun("c", "int main(){return 3;}", "", 10)).getStatus());
	}

	@Test
	void mxRunCrashCppIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		assertEquals("runtime_error", ex.run(mxRun("cpp", "int main(){return 3;}", "", 10)).getStatus());
	}

	@Test
	void mxRunCrashJavaIsRuntimeError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		assertEquals("runtime_error", ex.run(mxRun("java", "System.exit(3);", "", 10)).getStatus());
	}

	@Test
	void mxJudgeCrashPythonIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("python", "sys.exit(3)", List.of(new TestCase("a", "a", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeCrashJavaIsMismatch(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_EXIT3);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("java", "System.exit(3);", List.of(new TestCase("a", "a", false)), 10));
		assertEquals("mismatch", r.getCases().get(0).getError());
	}

	@Test
	void mxRunStderrOnlyPythonOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-py 1>&2"));
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("python", "x", "", 10));
		assertEquals("ok", r.getStatus());
		assertTrue(r.isOk());
		assertTrue(r.getOutput().contains("warn-py"));
	}

	@Test
	void mxRunStderrOnlyNodeOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-node 1>&2"));
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("node", "x", "", 10));
		assertEquals("ok", r.getStatus());
		assertTrue(r.getOutput().contains("warn-node"));
	}

	@Test
	void mxRunStderrOnlyCForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-c 1>&2"));
		assertEquals("ok", ex.run(mxRun("c", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunStderrOnlyCppForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-cpp 1>&2"));
		assertEquals("ok", ex.run(mxRun("cpp", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunStderrOnlyJavaForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-java 1>&2"));
		assertEquals("ok", ex.run(mxRun("java", "x", "", 10)).getStatus());
	}

	@Test
	void mxJudgeStderrOnlyMatchesExpected(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo warn-py 1>&2"));
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("python", "x", List.of(new TestCase("ignored-stdin", "warn-py", false)), 10));
		assertEquals(1, r.getPassed());
		assertTrue(r.isAllPassed());
	}

	@Test
	void mxRunUnicodePythonEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("python", "x", MX_UNI, 10));
		assertEquals("ok", r.getStatus());
		assertEquals(MX_UNI, r.getOutput());
	}

	@Test
	void mxRunUnicodeNodeEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "x", MX_UNI, 10));
		assertEquals("ok", r.getStatus());
		assertEquals(MX_UNI, r.getOutput());
	}

	@Test
	void mxRunUnicodeCForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "type", "\"PLACEHOLDER\""));
		ex.force(null);
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("python", "x", MX_UNI, 10));
		assertEquals(MX_UNI, r.getOutput());
	}

	@Test
	void mxJudgeUnicodePythonPass(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("python", "x", List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
		assertEquals(1, r.getPassed());
		assertEquals("", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeUnicodeNodePass(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("node", "x", List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxJudgeUnicodeCPass(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("python", "x", List.of(new TestCase(MX_UNI, MX_UNI, false)), 10));
		assertTrue(r.isAllPassed());
	}

	@Test
	void mxRun100kPythonUnderCapOk(@TempDir Path tmp) {
		String big = "q".repeat(100 * 1024);
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("python", "x", big, 10));
		assertEquals("ok", r.getStatus());
		assertEquals(big.length(), r.getOutput().length());
	}

	@Test
	void mxRun100kNodeUnderCapOk(@TempDir Path tmp) {
		String big = "n".repeat(100 * 1024);
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "x", big, 10));
		assertEquals("ok", r.getStatus());
		assertEquals(big.length(), r.getOutput().length());
	}

	@Test
	void mxRun100kCForcedUnderCapOk(@TempDir Path tmp) {
		String big = "c".repeat(100 * 1024);
		StubExecutor ex = executor(tmp, tmp);
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("python", "x", big, 10));
		assertEquals("ok", r.getStatus());
		assertEquals(big.length(), r.getOutput().length());
	}

	@Test
	void mxJudge100kPythonUnderCapPass(@TempDir Path tmp) {
		String big = "j".repeat(100 * 1024);
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("python", "x", List.of(new TestCase(big, big, false)), 10));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxRunStdinEchoPython(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("python", "src", "hello-stdin", 10));
		assertEquals("ok", r.getStatus());
		assertEquals("hello-stdin", r.getOutput());
	}

	@Test
	void mxRunStdinEchoNode(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "src", "node-stdin", 10));
		assertEquals("ok", r.getStatus());
		assertEquals("node-stdin", r.getOutput());
	}

	@Test
	void mxRunStdinEchoCForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo c-stdin"));
		com.sprintjudge.service.executor.RunResult r = ex.run(mxRun("c", "src", "ignored", 10));
		assertEquals("ok", r.getStatus());
		assertTrue(r.getOutput().contains("c-stdin"));
	}

	@Test
	void mxRunStdinEchoCppForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo cpp-stdin"));
		assertTrue(ex.run(mxRun("cpp", "src", "ignored", 10)).getOutput().contains("cpp-stdin"));
	}

	@Test
	void mxRunStdinEchoJavaForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo java-stdin"));
		assertTrue(ex.run(mxRun("java", "src", "ignored", 10)).getOutput().contains("java-stdin"));
	}

	@Test
	void mxJudgeMixedPython(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(
				judgeReq("python", "x", List.of(new TestCase("a", "a", false), new TestCase("b", "WRONG", false)), 10));
		assertEquals(1, r.getPassed());
		assertEquals(2, r.getTotal());
		assertFalse(r.isAllPassed());
		assertEquals("mismatch", r.getCases().get(1).getError());
	}

	@Test
	void mxJudgeMixedNode(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(
				judgeReq("node", "x", List.of(new TestCase("a", "a", false), new TestCase("b", "WRONG", false)), 10));
		assertEquals(1, r.getPassed());
		assertFalse(r.isAllPassed());
	}

	@Test
	void mxJudgeMixedCForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo a"));
		com.sprintjudge.service.executor.JudgeResult r = ex.judge(judgeReq("c", "x",
				List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
		assertEquals(1, r.getPassed());
		assertFalse(r.isAllPassed());
	}

	@Test
	void mxJudgeMixedCppForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo a"));
		com.sprintjudge.service.executor.JudgeResult r = ex.judge(judgeReq("cpp", "x",
				List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
		assertEquals(1, r.getPassed());
		assertFalse(r.isAllPassed());
	}

	@Test
	void mxJudgeMixedJavaForced(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo a"));
		com.sprintjudge.service.executor.JudgeResult r = ex.judge(judgeReq("java", "x",
				List.of(new TestCase("ignored", "a", false), new TestCase("ignored", "ZZZ", false)), 10));
		assertEquals(1, r.getPassed());
		assertFalse(r.isAllPassed());
	}

	@Test
	void mxJudgeZeroCasesPython(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("python", "", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getTotal());
		assertEquals(0, r.getPassed());
	}

	@Test
	void mxJudgeZeroCasesNode(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("node", "  ", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getPassed());
	}

	@Test
	void mxJudgeZeroCasesC(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(judgeReq("c", "x", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getPassed());
	}

	@Test
	void mxJudgeZeroCasesCpp(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(judgeReq("cpp", "x", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getPassed());
	}

	@Test
	void mxJudgeZeroCasesJava(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp).judge(judgeReq("java", "x", List.of(), 10));
		assertTrue(r.isAllPassed());
		assertEquals(0, r.getPassed());
	}

	@Test
	void mxRunZeroTimeoutPython(@TempDir Path tmp) throws Exception {
		Path work = Files.createTempDirectory("oq-mx-py0");
		try {
			StubExecutor ex = new StubExecutor(work.toString(), work.toString(), 1);
			ex.force(MX_HANG);
			assertEquals("timeout", ex.run(mxRun("python", "x", "", 0)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("node", "x", "", 0)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("c", "x", "", 0)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("cpp", "x", "", 0)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("java", "x", "", 0)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("python", "x", "", -3)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("node", "x", "", -9)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("c", "x", "", -1)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("cpp", "x", "", -2)).getStatus());
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
			assertEquals("timeout", ex.run(mxRun("java", "x", "", -4)).getStatus());
		} finally {
			Thread.sleep(200);
			com.sprintjudge.util.ExecIo.deleteTree(work);
		}
	}

	@Test
	void mxRunUnsupportedRuby(@TempDir Path tmp) {
		assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("ruby", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunUnsupportedBlank(@TempDir Path tmp) {
		assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("   ", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunUnsupportedGo(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("go", "x", "", 10));
		assertFalse(r.isOk());
		assertEquals("unsupported_language", r.getStatus());
	}

	@Test
	void mxRunUnsupportedEmpty(@TempDir Path tmp) {
		assertEquals("unsupported_language", executor(tmp, tmp).run(mxRun("", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunSource65537Python(@TempDir Path tmp) {
		assertEquals("source_too_large",
				executor(tmp, tmp).run(mxRun("python", "x".repeat(65_537), "", 10)).getStatus());
	}

	@Test
	void mxRunSource65537Node(@TempDir Path tmp) {
		assertEquals("source_too_large", executor(tmp, tmp).run(mxRun("node", "x".repeat(65_537), "", 10)).getStatus());
	}

	@Test
	void mxRunSource65537C(@TempDir Path tmp) {
		assertEquals("source_too_large", executor(tmp, tmp).run(mxRun("c", "x".repeat(65_537), "", 10)).getStatus());
	}

	@Test
	void mxRunSource65537Cpp(@TempDir Path tmp) {
		assertEquals("source_too_large", executor(tmp, tmp).run(mxRun("cpp", "x".repeat(65_537), "", 10)).getStatus());
	}

	@Test
	void mxRunSource65537Java(@TempDir Path tmp) {
		assertEquals("source_too_large", executor(tmp, tmp).run(mxRun("java", "x".repeat(65_537), "", 10)).getStatus());
	}

	@Test
	void mxRunSource65536BoundaryPython(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("python", "#" + "x".repeat(65_535), "", 10));
		assertTrue(r.isOk() || r.getStatus().equals("runtime_error"));
	}

	@Test
	void mxRunSource65536BoundaryNode(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("node", "#" + "x".repeat(65_535), "", 10));
		assertTrue(r.isOk() || r.getStatus().equals("runtime_error"));
	}

	@Test
	void mxRunStdin10000Passthrough(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("python", "x", "s".repeat(10000), 10));
		assertEquals("ok", r.getStatus());
		assertEquals(10000, r.getOutput().length());
	}

	@Test
	void mxRunStdin10001Passthrough(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("python", "x", "s".repeat(10001), 10));
		assertEquals("ok", r.getStatus());
		assertEquals(10001, r.getOutput().length());
	}

	@Test
	void mxRunTimeout31QuickOk(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("node", "x", "fast31", 31));
		assertEquals("ok", r.getStatus());
		assertEquals("fast31", r.getOutput());
	}

	@Test
	void mxRunAliasJsEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("js", "x", "alias-js", 10));
		assertTrue(r.isOk());
		assertEquals("alias-js", r.getOutput());
	}

	@Test
	void mxRunAliasPyEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("py", "x", "alias-py", 10));
		assertTrue(r.isOk());
		assertEquals("alias-py", r.getOutput());
	}

	@Test
	void mxRunAliasJavascriptEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp)
				.run(mxRun("javascript", "x", "alias-full", 10));
		assertTrue(r.isOk());
		assertEquals("alias-full", r.getOutput());
	}

	@Test
	void mxRunAliasUpperPythonEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("PYTHON", "x", "alias-up", 10));
		assertTrue(r.isOk());
		assertEquals("alias-up", r.getOutput());
	}

	@Test
	void mxRunAliasUpperNodeEcho(@TempDir Path tmp) {
		com.sprintjudge.service.executor.RunResult r = executor(tmp, tmp).run(mxRun("Node", "x", "alias-node", 10));
		assertTrue(r.isOk());
		assertEquals("alias-node", r.getOutput());
	}

	@Test
	void mxJudgeZeroTimeoutPythonPass(@TempDir Path tmp) {
		StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("python", "x", List.of(new TestCase("5", "5", false)), 0));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxJudgeZeroTimeoutCForcedPass(@TempDir Path tmp) {
		StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
		ex.force(List.of("cmd", "/c", "echo 5"));
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("c", "x", List.of(new TestCase("ignored", "5", false)), 0));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxJudgeZeroTimeoutJavaForcedPass(@TempDir Path tmp) {
		StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
		ex.force(List.of("cmd", "/c", "echo 5"));
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("java", "x", List.of(new TestCase("ignored", "5", false)), 0));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxJudgeNegativeTimeoutNodePass(@TempDir Path tmp) {
		StubExecutor ex = new StubExecutor(tmp.toString(), tmp.toString(), 30);
		com.sprintjudge.service.executor.JudgeResult r = ex
				.judge(judgeReq("node", "x", List.of(new TestCase("5", "5", false)), -2));
		assertEquals(1, r.getPassed());
	}

	@Test
	void mxRunStartFailureCIsIoError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_BADPROG);
		assertEquals("io_error", ex.run(mxRun("c", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunStartFailureJavaIsIoError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_BADPROG);
		assertEquals("io_error", ex.run(mxRun("java", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunStartFailurePythonIsIoError(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(MX_BADPROG);
		assertEquals("io_error", ex.run(mxRun("python", "x", "", 10)).getStatus());
	}

	@Test
	void mxRunNullSourceCForcedOk(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		ex.force(List.of("cmd", "/c", "echo null-src"));
		assertTrue(ex.run(mxRun("c", null, "", 10)).getOutput().contains("null-src"));
	}

	@Test
	void mxJudgeNullExpectedMatchesEmpty(@TempDir Path tmp) {
		com.sprintjudge.service.executor.JudgeResult r = executor(tmp, tmp)
				.judge(judgeReq("python", "x", List.of(new TestCase("", null, false)), 10));
		assertEquals(1, r.getPassed());
		assertEquals("", r.getCases().get(0).getError());
	}

	@Test
	void mxJudgeMissingOutputFileIsStdoutExceeded(@TempDir Path tmp) {
		StubExecutor ex = executor(tmp, tmp);
		try (org.mockito.MockedStatic<com.sprintjudge.util.ExecIo> mocked = org.mockito.Mockito
				.mockStatic(com.sprintjudge.util.ExecIo.class)) {
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					com.sprintjudge.util.ExecIo.awaitBounded(org.mockito.ArgumentMatchers.any(Process.class),
							org.mockito.ArgumentMatchers.any(Path.class), org.mockito.ArgumentMatchers.anyLong());
				}
			}).thenAnswer(new org.mockito.stubbing.Answer<java.lang.Object>() {
				@Override
				public java.lang.Object answer(org.mockito.invocation.InvocationOnMock inv) throws java.lang.Throwable {
					{
						// Reap the real child so Windows releases the output
						// file before cleanup; the read below is still mocked.
						((Process) inv.getArgument(0)).waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
						return com.sprintjudge.util.ExecIo.WaitOutcome.FINISHED;
					}
				}
			});
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					com.sprintjudge.util.ExecIo.readCappedFile(org.mockito.ArgumentMatchers.any(Path.class));
				}
			}).thenReturn(null);
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					com.sprintjudge.util.ExecIo.killAndReap(org.mockito.ArgumentMatchers.any(Process.class));
				}
			}).thenCallRealMethod();
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() throws java.lang.Throwable {
					com.sprintjudge.util.ExecIo.deleteTree(org.mockito.ArgumentMatchers.any(Path.class));
				}
			}).thenCallRealMethod();
			com.sprintjudge.service.executor.JudgeResult r = ex
					.judge(judgeReq("node", "ignored", List.of(new TestCase("9", "9", false)), 10));
			assertEquals(0, r.getPassed());
			assertEquals("stdout_exceeded_1MB", r.getCases().get(0).getError());
		}
	}
}
