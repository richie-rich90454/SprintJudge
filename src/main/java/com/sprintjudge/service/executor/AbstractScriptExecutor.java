package com.sprintjudge.service.executor;

import com.sprintjudge.util.ExecIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractScriptExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AbstractScriptExecutor.class);

    /** Client-supplied language values are canonicalised before any path is built. */
    private static final Map<String, String> CANONICAL = Map.of(
            "javascript", "node", "js", "node", "py", "python");
    private static final Set<String> SUPPORTED = Set.of("c", "cpp", "java", "node", "python");

    @Value("${sprintjudge.executor.work-dir:./executor/tmp}")
    protected String workDirBase;

    @Value("${sprintjudge.executor.compile-scripts-dir:./executor/compile-scripts}")
    protected String scriptsDir;

    @Value("${sprintjudge.executor.timeout-sec:10}")
    protected int defaultTimeoutSec;

    /** Command to execute for one test case. Stdin is wired to the input file by the base class. */
    protected abstract List<String> commandFor(String language, Path sourceFile, Path inputFile, Path runDir);

    protected final Path scriptPath(String language) {
        return Path.of(scriptsDir, language + ".sh");
    }

    @Override
    public boolean supports(String language) {
        return SUPPORTED.contains(canonical(language));
    }

    protected String canonical(String language) {
        String lower = language == null ? "" : language.toLowerCase();
        return CANONICAL.getOrDefault(lower, lower);
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        String language = canonical(request.language());
        if (!SUPPORTED.contains(language)) {
            log.warn("Rejected unsupported language: {}", request.language());
            return new JudgeResult(0, request.testCases().size(), false, List.of(
                    new JudgeResult.CaseResult(0, false, "", "", "unsupported_language")));
        }
        Path runDir;
        try {
            runDir = Files.createDirectories(Path.of(workDirBase,
                    "run-" + Thread.currentThread().threadId() + "-" + System.nanoTime()));
        } catch (IOException e) {            log.error("Could not create run directory under {}", workDirBase, e);
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        }
        Process proc = null;
        try {
            // Absolutize BEFORE handing any path to a child process: ProcessBuilder
            // resolves relative argv paths against pb.directory(), which would double
            // the relative work-dir segment.
            runDir = runDir.toAbsolutePath();
            String ext = extension(language);
            String judgeFileName = language.equals("java") ? "Main" : "solution";
            Path sourceFile = runDir.resolve(judgeFileName + ext).toAbsolutePath();
            Files.writeString(sourceFile, request.sourceCode() == null ? "" : request.sourceCode());

            List<JudgeResult.CaseResult> results = new ArrayList<>();
            int passed = 0;
            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;
            for (int idx = 0; idx < request.testCases().size(); idx++) {
                TestCase tc = request.testCases().get(idx);
                Path inputFile = runDir.resolve("input_" + idx + ".txt").toAbsolutePath();
                Files.writeString(inputFile, tc.input() == null ? "" : tc.input());
                Path outputFile = runDir.resolve("out_" + idx + ".txt").toAbsolutePath();

                List<String> cmd = commandFor(language, sourceFile, inputFile, runDir);
                ProcessBuilder pb = new ProcessBuilder(cmd)
                        .directory(runDir.toFile())
                        .redirectErrorStream(true)
                        .redirectInput(inputFile.toFile())
                        // File redirect: a child blocked writing a full pipe must
                        // not turn into a bogus timeout (waitFor + read race).
                        .redirectOutput(outputFile.toFile());
                proc = pb.start();

                ExecIo.WaitOutcome outcome = ExecIo.awaitBounded(proc, outputFile, timeout);
                if (outcome != ExecIo.WaitOutcome.FINISHED) {
                    ExecIo.killAndReap(proc);
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "",
                            outcome == ExecIo.WaitOutcome.TOO_BIG ? "stdout_exceeded_1MB" : "timeout"));
                    continue;
                }
                // Edge case X: cap captured stdout at 1MB.
                String output = ExecIo.readCappedFile(outputFile);
                if (output == null) {
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "stdout_exceeded_1MB"));
                    continue;
                }
                boolean ok = output.equals((tc.expectedOutput() == null ? "" : tc.expectedOutput()).strip());
                if (ok) passed++;
                results.add(new JudgeResult.CaseResult(idx, ok, tc.expectedOutput(), output, ok ? "" : "mismatch"));
            }
            return new JudgeResult(passed, request.testCases().size(), passed == request.testCases().size(), results);
        } catch (IOException e) {
            if (proc != null) ExecIo.killAndReap(proc);
            log.error("Judge execution failed for language {}", language, e);
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        } finally {
            ExecIo.deleteTree(runDir);
        }
    }

    private String extension(String language) {
        return switch (language) {
            case "c" -> ".c";
            case "cpp" -> ".cpp";
            case "java" -> ".java";
            case "node" -> ".js";
            case "python" -> ".py";
            default -> ".txt";
        };
    }

    /**
     * Live execution for the interactive console: writes stdin to a file, then
     * runs the per-language script (which compiles + executes) reading that file.
     * Captures combined stdout/stderr so students get immediate feedback.
     */
    @Override
    public RunResult run(RunRequest request) {
        String language = canonical(request.language());
        if (!SUPPORTED.contains(language)) {
            return new RunResult(false, "", "", "unsupported_language");
        }
        if (request.sourceCode() != null && request.sourceCode().length() > 65_536) {
            return new RunResult(false, "", "", "source_too_large");
        }
        Path runDir;
        try {
            runDir = Files.createDirectories(Path.of(workDirBase,
                    "run-" + Thread.currentThread().threadId() + "-" + System.nanoTime()));
        } catch (IOException e) {
            log.error("Could not create run directory under {}", workDirBase, e);
            return new RunResult(false, "", "", "io_error");
        }
        Process proc = null;
        try {
            runDir = runDir.toAbsolutePath();
            String runFileName = language.equals("java") ? "Main" : "solution";
            Path sourceFile = runDir.resolve(runFileName + extension(language)).toAbsolutePath();
            Files.writeString(sourceFile, request.sourceCode() == null ? "" : request.sourceCode());
            Path inputFile = runDir.resolve("input.txt").toAbsolutePath();
            Files.writeString(inputFile, request.stdin() == null ? "" : request.stdin());

            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;
            List<String> cmd = commandFor(language, sourceFile, inputFile, runDir);
            Path outputFile = runDir.resolve("stdout.txt").toAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(runDir.toFile())
                    .redirectErrorStream(true)
                    .redirectInput(inputFile.toFile())
                    // File redirect (not a pipe): verbose console runs must
                    // not deadlock the wait the way the old pipe read did.
                    .redirectOutput(outputFile.toFile());
            proc = pb.start();

            ExecIo.WaitOutcome outcome = ExecIo.awaitBounded(proc, outputFile, timeout);
            if (outcome != ExecIo.WaitOutcome.FINISHED) {
                ExecIo.killAndReap(proc);
                return new RunResult(false, "", "",
                        outcome == ExecIo.WaitOutcome.TOO_BIG ? "stdout_exceeded_1MB" : "timeout");
            }
            String output = ExecIo.readCappedFile(outputFile);
            if (output == null) {
                return new RunResult(false, "", "", "stdout_exceeded_1MB");
            }
            boolean ok = proc.exitValue() == 0;
            return new RunResult(ok, output, "", ok ? "ok" : "runtime_error");
        } catch (IOException e) {
            ExecIo.killAndReap(proc);
            log.error("Run execution failed for language {}", language, e);
            return new RunResult(false, "", "", "io_error");
        } finally {
            ExecIo.deleteTree(runDir);
        }
    }
}
