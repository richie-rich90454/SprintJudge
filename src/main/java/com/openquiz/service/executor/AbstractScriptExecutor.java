package com.openquiz.service.executor;

import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
                if (!finished) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "timeout"));
                    idx++;
                    continue;
                }
                // Edge case X: cap captured stdout at 1MB; kill the process if exceeded.
                String output = readCapped(proc);
                if (output == null) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "stdout_exceeded_1MB"));
                    idx++;
                    continue;
                }
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

    /**
     * Reads stdout up to 1MB. Returns null if the limit is exceeded (caller
     * should kill the process). Edge case X.
     */
    private String readCapped(Process proc) {
        final int CAP = 1_048_576;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        try (InputStream in = proc.getInputStream()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > CAP) return null;
                baos.write(buf, 0, n);
            }
        } catch (IOException e) {
            return "";
        }
        return baos.toString(StandardCharsets.UTF_8).trim();
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
