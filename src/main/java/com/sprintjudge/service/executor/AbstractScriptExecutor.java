package com.openquiz.service.executor;

import com.openquiz.util.ExecIo;
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
import java.util.concurrent.TimeUnit;

public abstract class AbstractScriptExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(AbstractScriptExecutor.class);

    /** Client-supplied language values are canonicalised before any path is built. */
    private static final Map<String, String> CANONICAL = Map.of(
            "javascript", "node", "js", "node", "py", "python");
    private static final Set<String> SUPPORTED = Set.of("c", "cpp", "java", "node", "python");

    @Value("${openquiz.executor.work-dir:./executor/tmp}")
    protected String workDirBase;

    @Value("${openquiz.executor.compile-scripts-dir:./executor/compile-scripts}")
    protected String scriptsDir;

    @Value("${openquiz.executor.timeout-sec:10}")
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
        try {
            // Absolutize BEFORE handing any path to a child process: ProcessBuilder
            // resolves relative argv paths against pb.directory(), which would double
            // the relative work-dir segment.
            runDir = runDir.toAbsolutePath();
            String ext = extension(language);
            Path sourceFile = runDir.resolve("solution" + ext).toAbsolutePath();
            Files.writeString(sourceFile, request.sourceCode() == null ? "" : request.sourceCode());

            List<JudgeResult.CaseResult> results = new ArrayList<>();
            int passed = 0;
            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;
            for (int idx = 0; idx < request.testCases().size(); idx++) {
                TestCase tc = request.testCases().get(idx);
                Path inputFile = runDir.resolve("input_" + idx + ".txt").toAbsolutePath();
                Files.writeString(inputFile, tc.input() == null ? "" : tc.input());

                List<String> cmd = commandFor(language, sourceFile, inputFile, runDir);
                ProcessBuilder pb = new ProcessBuilder(cmd)
                        .directory(runDir.toFile())
                        .redirectErrorStream(true)
                        .redirectInput(inputFile.toFile());
                Process proc = pb.start();

                if (!proc.waitFor(timeout, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "timeout"));
                    continue;
                }
                // Edge case X: cap captured stdout at 1MB; kill the process if exceeded.
                String output = ExecIo.readCapped(proc);
                if (output == null) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "stdout_exceeded_1MB"));
                    continue;
                }
                boolean ok = output.equals((tc.expectedOutput() == null ? "" : tc.expectedOutput()).strip());
                if (ok) passed++;
                results.add(new JudgeResult.CaseResult(idx, ok, tc.expectedOutput(), output, ok ? "" : "mismatch"));
            }
            return new JudgeResult(passed, request.testCases().size(), passed == request.testCases().size(), results);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
