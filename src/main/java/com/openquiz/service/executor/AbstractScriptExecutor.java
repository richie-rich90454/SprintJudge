package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class AbstractScriptExecutor implements CodeExecutor {

    @Value("${openquiz.executor.work-dir:./executor/tmp}")
    protected String workDirBase;

    @Value("${openquiz.executor.compile-scripts-dir:./executor/compile-scripts}")
    protected String scriptsDir;

    @Value("${openquiz.executor.timeout-sec:10}")
    protected int defaultTimeoutSec;

    protected abstract List<String> buildCommand(String scriptPath, String sourceFile, String inputFile, String runDir);

    @Override
    public boolean supports(String language) {
        return switch (language.toLowerCase()) {
            case "c", "cpp", "java", "node", "javascript", "js", "python", "py" -> true;
            default -> false;
        };
    }

    @Override
    public JudgeResult judge(JudgeRequest request) {
        Path runDir;
        try {
            runDir = Files.createDirectories(Path.of(workDirBase, "run-" + Thread.currentThread().threadId() + "-" + System.nanoTime()));
        } catch (IOException e) {
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        }
        String ext = extension(request.language());
        Path sourceFile = runDir.resolve("solution" + ext);
        try {
            Files.writeString(sourceFile, request.sourceCode());

            List<JudgeResult.CaseResult> results = new ArrayList<>();
            int passed = 0;
            int idx = 0;
            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;
            for (TestCase tc : request.testCases()) {
                Path inputFile = runDir.resolve("input_" + idx + ".txt");
                Files.writeString(inputFile, tc.input() == null ? "" : tc.input());
                String script = Path.of(scriptsDir, request.language().toLowerCase() + ".sh").toString();

                ProcessBuilder pb = new ProcessBuilder(buildCommand(script, sourceFile.toString(), inputFile.toString(), runDir.toString()));
                pb.directory(runDir.toFile());
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                boolean finished = proc.waitFor(timeout, TimeUnit.SECONDS);
                String output;
                if (!finished) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "timeout"));
                    idx++;
                    continue;
                }
                output = new String(proc.getInputStream().readAllBytes()).trim();
                boolean ok = output.equals((tc.expectedOutput() == null ? "" : tc.expectedOutput()).strip());
                if (ok) passed++;
                results.add(new JudgeResult.CaseResult(idx, ok, tc.expectedOutput(), output, ok ? "" : "mismatch"));
                idx++;
            }
            return new JudgeResult(passed, request.testCases().size(), passed == request.testCases().size(), results);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        } finally {
            deleteRecursively(runDir);
        }
    }

    private String extension(String language) {
        return switch (language.toLowerCase()) {
            case "c" -> ".c";
            case "cpp" -> ".cpp";
            case "java" -> ".java";
            case "node", "javascript", "js" -> ".js";
            case "python", "py" -> ".py";
            default -> ".txt";
        };
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
