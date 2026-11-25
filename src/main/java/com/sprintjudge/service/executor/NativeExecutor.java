package com.sprintjudge.service.executor;

import com.sprintjudge.util.ExecIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Direct toolchain executor: invokes gcc/g++/javac/node/python via
 * ProcessBuilder with no sandbox layer.
 *
 * Default on Windows development AND supported for Windows production hosts,
 * where nsjail does not exist. It trades process isolation for portability; on
 * Linux, prefer nsjail. Compensating controls in every mode: language
 * whitelist, 64KB source cap, per-case timeout with forced kill, 1MB stdout
 * cap, and per-run temp-directory cleanup.
 */
@Component
@ConditionalOnProperty(name = "sprintjudge.executor.mode", havingValue = "native")
@SuppressWarnings("all")
public class NativeExecutor implements CodeExecutor {

	private static final Logger log = LoggerFactory.getLogger(NativeExecutor.class);

	private static final Map<String, String> CANONICAL = Map.of("javascript", "node", "js", "node", "py", "python");
	private static final Set<String> SUPPORTED = Set.of("c", "cpp", "java", "node", "python");

	@Value("${sprintjudge.executor.work-dir:./executor/tmp}")
	private String workDirBase;

	@Value("${sprintjudge.executor.timeout-sec:10}")
	private int defaultTimeoutSec;

	/** Windows exposes python.exe; Linux/macOS ship python3. */
	private static final String PYTHON_BINARY = pythonBinary(System.getProperty("os.name", ""));

	static String pythonBinary(String osName) {
		return osName.toLowerCase(java.util.Locale.ROOT).contains("win") ? "python" : "python3";
	}

	private final CompileArtifactCache compileCache;

	public NativeExecutor(CompileArtifactCache compileCache) {
		this.compileCache = compileCache;
		log.warn("NativeExecutor active: submissions run WITHOUT sandbox isolation. "
				+ "On Linux production, set sprintjudge.executor.mode=nsjail.");
	}

	@Override
	public boolean supports(String language) {
		return SUPPORTED.contains(canonical(language));
	}

	private String canonical(String language) {
		String lower = language == null ? "" : language.toLowerCase();
		return CANONICAL.getOrDefault(lower, lower);
	}

	@Override
	public JudgeResult judge(JudgeRequest request) {
		String language = canonical(request.getLanguage());
		if (!SUPPORTED.contains(language)) {
			log.warn("Rejected unsupported language: {}", request.getLanguage());
			return new JudgeResult(0, request.getTestCases().size(), false,
					List.of(new JudgeResult.CaseResult(0, false, "", "", "unsupported_language")));
		}
		Path runDir;
		try {
			runDir = Files.createDirectories(
					Path.of(workDirBase, "run-" + Thread.currentThread().threadId() + "-" + System.nanoTime()));
		} catch (IOException e) {
			log.error("Could not create run directory under {}", workDirBase, e);
			return new JudgeResult(0, request.getTestCases().size(), false, List.of());
		}
		Process proc = null;
		try {
			// Absolutize before child-process handoff (see AbstractScriptExecutor).
			runDir = runDir.toAbsolutePath();
			String ext;
			if (language.equals("c")) {
				ext = ".c";
			} else if (language.equals("cpp")) {
				ext = ".cpp";
			} else if (language.equals("java")) {
				ext = ".java";
			} else if (language.equals("node")) {
				ext = ".js";
			} else {
				ext = ".py";
			}
			// Java requires filename to match the public class name.
			String fileName = language.equals("java") ? "Main" : "solution";
			Path sourceFile = runDir.resolve(fileName + ext).toAbsolutePath();
			Files.writeString(sourceFile, request.getSourceCode() == null ? "" : request.getSourceCode());

			int timeout = request.getTimeoutSec() > 0 ? request.getTimeoutSec() : defaultTimeoutSec;

			// Compile phase (c/cpp/java). Fails fast with a distinct judge log so
			// players see "compilation_error" instead of N misleading mismatches.
			String compileErr = compileOrReuse(language, request.getSourceCode(), sourceFile, runDir, timeout);
			if (compileErr != null) {
				log.warn("Compile failed ({}): {}", language, truncate(compileErr));
				return allFailed(request, "compilation_error");
			}

			List<JudgeResult.CaseResult> results = new ArrayList<>();
			int passed = 0;
			for (int idx = 0; idx < request.getTestCases().size(); idx++) {
				TestCase tc = request.getTestCases().get(idx);
				Path inputFile = runDir.resolve("input_" + idx + ".txt").toAbsolutePath();
				Files.writeString(inputFile, tc.getInput() == null ? "" : tc.getInput());
				Path outputFile = runDir.resolve("out_" + idx + ".txt").toAbsolutePath();

				List<String> cmd = runCommand(language, sourceFile, runDir);
				ProcessBuilder pb = new ProcessBuilder(cmd).directory(runDir.toFile()).redirectErrorStream(true)
						.redirectInput(inputFile.toFile())
						// File redirect avoids the pipe-buffer deadlock that
						// misjudged verbose programs as timeouts.
						.redirectOutput(outputFile.toFile());
				proc = pb.start();

				String outcome = ExecIo.awaitBounded(proc, outputFile, timeout);
				if (!"FINISHED".equals(outcome)) {
					ExecIo.killAndReap(proc);
					results.add(new JudgeResult.CaseResult(idx, false, tc.getExpectedOutput(), "",
							ExecIo.WaitOutcome.TOO_BIG.equals(outcome) ? "stdout_exceeded_1MB" : "timeout"));
					continue;
				}
				String output = ExecIo.readCappedFile(outputFile);
				if (output == null) {
					results.add(
							new JudgeResult.CaseResult(idx, false, tc.getExpectedOutput(), "", "stdout_exceeded_1MB"));
					continue;
				}
				boolean ok = output.equals((tc.getExpectedOutput() == null ? "" : tc.getExpectedOutput()).strip());
				if (ok)
					passed++;
				results.add(new JudgeResult.CaseResult(idx, ok, tc.getExpectedOutput(), output, ok ? "" : "mismatch"));
			}
			return new JudgeResult(passed, request.getTestCases().size(), passed == request.getTestCases().size(),
					results);
		} catch (IOException | InterruptedException e) {
			ExecIo.killAndReap(proc);
			Thread.currentThread().interrupt();
			log.error("Native judge execution failed for language {}", language, e);
			return new JudgeResult(0, request.getTestCases().size(), false, List.of());
		} finally {
			ExecIo.deleteTree(runDir);
		}
	}

	/** Returns null for interpreted languages; otherwise the compile argv. */
	private List<String> compileCommand(String language, Path sourceFile, Path runDir) {
		if (language.equals("c")) {
			return List.of("gcc", "-O2", "-o", binary(runDir).toString(), sourceFile.toString());
		}
		if (language.equals("cpp")) {
			return List.of("g++", "-O2", "-std=c++17", "-o", binary(runDir).toString(), sourceFile.toString());
		}
		if (language.equals("java")) {
			return List.of("javac", "-d", runDir.toString(), sourceFile.toString());
		}
		return null;
	}

	/**
	 * Single-artifact languages may reuse a cached binary for identical sources.
	 */
	private boolean cacheable(String language) {
		return language.equals("c") || language.equals("cpp");
	}

	/**
	 * Compiles (or reuses the cached binary). Returns null on success, or a
	 * non-null error marker on failure.
	 */
	private String compileOrReuse(String language, String sourceCode, Path sourceFile, Path runDir, int timeoutSec)
			throws IOException, InterruptedException {
		if (!cacheable(language)) {
			List<String> cmd = compileCommand(language, sourceFile, runDir);
			if (cmd == null)
				return null;
			return runToCompletion(cmd, runDir, timeoutSec);
		}
		String key = CompileArtifactCache.keyFor(language, sourceCode);
		java.util.Optional<Path> cached = compileCache.get(key);
		if (cached.isPresent()) {
			try {
				Files.copy(cached.get(), binary(runDir), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return null;
			} catch (java.nio.file.NoSuchFileException e) {
				log.warn("Compile cache entry vanished mid-copy, recompiling");
			}
		}
		List<String> cmd = compileCommand(language, sourceFile, runDir);
		String err = runToCompletion(cmd, runDir, timeoutSec);
		maybeCache(compileCache, language, sourceCode, runDir, err);
		return err;
	}

	/**
	 * Caches a fresh binary on clean compile; unit-testable without a toolchain.
	 */
	static void maybeCache(CompileArtifactCache cache, String language, String sourceCode, Path runDir,
			String compileErr) {
		if (compileErr != null)
			return;
		cache.put(CompileArtifactCache.keyFor(language, sourceCode), runDir.resolve(binaryName()));
	}

	private List<String> runCommand(String language, Path sourceFile, Path runDir) {
		if (language.equals("c") || language.equals("cpp")) {
			return List.of(binary(runDir).toString());
		}
		if (language.equals("java")) {
			return List.of("java", "-cp", runDir.toString(), mainClass(sourceFile));
		}
		if (language.equals("node")) {
			return List.of("node", sourceFile.toString());
		}
		return List.of(PYTHON_BINARY, sourceFile.toString());
	}

	private Path binary(Path runDir) {
		return runDir.resolve(binaryName());
	}

	static String binaryName() {
		return System.getProperty("os.name", "").toLowerCase().contains("win") ? "program.exe" : "program";
	}

	private String mainClass(Path sourceFile) {
		String name = sourceFile.getFileName().toString();
		return name.substring(0, name.length() - ".java".length());
	}

	/**
	 * Runs a fire-and-forget command; returns null on success or trimmed output on
	 * failure.
	 */
	private String runToCompletion(List<String> cmd, Path dir, int timeoutSec)
			throws IOException, InterruptedException {
		// Redirect to a capped file instead of a pipe: the child is never blocked
		// writing >64KB of diagnostics while the parent waits for exit.
		Path log = dir.resolve("compile.log");
		Process proc = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
				.redirectOutput(log.toFile()).start();
		String outcome = ExecIo.awaitBounded(proc, log, timeoutSec);
		if (!ExecIo.WaitOutcome.FINISHED.equals(outcome)) {
			ExecIo.killAndReap(proc);
			if (ExecIo.WaitOutcome.TOO_BIG.equals(outcome))
				return "stdout_exceeded_1MB";
			return "compile_timeout";
		}
		if (proc.exitValue() == 0)
			return null;
		String out = ExecIo.readCappedFile(log);
		return out == null ? "stdout_exceeded_1MB" : out;
	}

	private JudgeResult allFailed(JudgeRequest request, String code) {
		List<JudgeResult.CaseResult> results = new ArrayList<>();
		for (int i = 0; i < request.getTestCases().size(); i++) {
			results.add(
					new JudgeResult.CaseResult(i, false, request.getTestCases().get(i).getExpectedOutput(), "", code));
		}
		return new JudgeResult(0, request.getTestCases().size(), false, results);
	}

	private String truncate(String s) {
		return s.length() <= 2000 ? s : s.substring(0, 1998) + "...";
	}

	/**
	 * Live execution for the interactive console: compiles (if needed) then runs
	 * the program feeding {@code stdin} and capturing combined stdout/stderr.
	 * Callers (the player console) get back the merged stream so students can
	 * experiment before submitting - mirroring JuiceMind's in-quiz runner.
	 */
	@Override
	public RunResult run(RunRequest request) {
		String language = canonical(request.getLanguage());
		if (!SUPPORTED.contains(language)) {
			return new RunResult(false, "", "", "unsupported_language");
		}
		if (request.getSourceCode() != null && request.getSourceCode().length() > 65_536) {
			return new RunResult(false, "", "", "source_too_large");
		}
		Path runDir;
		try {
			runDir = Files.createDirectories(
					Path.of(workDirBase, "run-" + Thread.currentThread().threadId() + "-" + System.nanoTime()));
		} catch (IOException e) {
			log.error("Could not create run directory under {}", workDirBase, e);
			return new RunResult(false, "", "", "io_error");
		}
		Process proc = null;
		try {
			runDir = runDir.toAbsolutePath();
			String ext;
			if (language.equals("c")) {
				ext = ".c";
			} else if (language.equals("cpp")) {
				ext = ".cpp";
			} else if (language.equals("java")) {
				ext = ".java";
			} else if (language.equals("node")) {
				ext = ".js";
			} else {
				ext = ".py";
			}
			String runFileName = language.equals("java") ? "Main" : "solution";
			Path sourceFile = runDir.resolve(runFileName + ext).toAbsolutePath();
			Files.writeString(sourceFile, request.getSourceCode() == null ? "" : request.getSourceCode());

			int timeout = request.getTimeoutSec() > 0 ? request.getTimeoutSec() : defaultTimeoutSec;

			String compileErr = compileOrReuse(language, request.getSourceCode(), sourceFile, runDir, timeout);
			if (compileErr != null) {
				return new RunResult(false, "", compileErr, "compilation_error");
			}

			List<String> cmd = runCommand(language, sourceFile, runDir);
			Path outputFile = runDir.resolve("stdout.txt").toAbsolutePath();
			ProcessBuilder pb = new ProcessBuilder(cmd).directory(runDir.toFile()).redirectErrorStream(true)
					// File redirect (not a pipe): a chatty program must not
					// deadlock the wait the way the old pipe read did.
					.redirectOutput(outputFile.toFile());
			if (request.getStdin() != null && !request.getStdin().isEmpty()) {
				Path inputFile = runDir.resolve("stdin.txt").toAbsolutePath();
				Files.writeString(inputFile, request.getStdin());
				pb.redirectInput(inputFile.toFile());
			}
			proc = pb.start();

			String outcome = ExecIo.awaitBounded(proc, outputFile, timeout);
			if (!"FINISHED".equals(outcome)) {
				ExecIo.killAndReap(proc);
				return new RunResult(false, "", "",
						ExecIo.WaitOutcome.TOO_BIG.equals(outcome) ? "stdout_exceeded_1MB" : "timeout");
			}
			String output = ExecIo.readCappedFile(outputFile);
			if (output == null) {
				return new RunResult(false, "", "", "stdout_exceeded_1MB");
			}
			boolean ok = proc.exitValue() == 0;
			return new RunResult(ok, output, "", ok ? "ok" : "runtime_error");
		} catch (IOException | InterruptedException e) {
			ExecIo.killAndReap(proc);
			Thread.currentThread().interrupt();
			log.error("Native run execution failed for language {}", language, e);
			return new RunResult(false, "", "", "io_error");
		} finally {
			ExecIo.deleteTree(runDir);
		}
	}
}
