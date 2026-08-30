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
import java.util.concurrent.TimeUnit;

/**
 * Direct toolchain executor: invokes gcc/g++/javac/node/python via
 * ProcessBuilder with no sandbox layer.
 *
 * Default on Windows development AND supported for Windows production hosts,
 * where nsjail does not exist. It trades process isolation for portability;
 * on Linux, prefer nsjail. Compensating controls in every mode: language
 * whitelist, 64KB source cap, per-case timeout with forced kill, 1MB stdout
 * cap, and per-run temp-directory cleanup.
 */
@Component
@ConditionalOnProperty(name = "sprintjudge.executor.mode", havingValue = "native")
public class NativeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(NativeExecutor.class);

    private static final Map<String, String> CANONICAL = Map.of(
            "javascript", "node", "js", "node", "py", "python");
    private static final Set<String> SUPPORTED = Set.of("c", "cpp", "java", "node", "python");

    @Value("${sprintjudge.executor.work-dir:./executor/tmp}")
    private String workDirBase;

    @Value("${sprintjudge.executor.timeout-sec:10}")
    private int defaultTimeoutSec;

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
        } catch (IOException e) {
            log.error("Could not create run directory under {}", workDirBase, e);
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        }
        try {
            // Absolutize before child-process handoff (see AbstractScriptExecutor).
            runDir = runDir.toAbsolutePath();
            String ext = switch (language) {
                case "c" -> ".c";
                case "cpp" -> ".cpp";
                case "java" -> ".java";
                case "node" -> ".js";
                default -> ".py";
            };
            // Java requires filename to match the public class name.
            String fileName = language.equals("java") ? "Main" : "solution";
            Path sourceFile = runDir.resolve(fileName + ext).toAbsolutePath();
            Files.writeString(sourceFile, request.sourceCode() == null ? "" : request.sourceCode());

            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;

            // Compile phase (c/cpp/java). Fails fast with a distinct judge log so
            // players see "compilation_error" instead of N misleading mismatches.
            String compileErr = compileOrReuse(language, request.sourceCode(), sourceFile, runDir, timeout);
            if (compileErr != null) {
                log.warn("Compile failed ({}): {}", language, truncate(compileErr));
                return allFailed(request, "compilation_error", compileErr);
            }

            List<JudgeResult.CaseResult> results = new ArrayList<>();
            int passed = 0;
            for (int idx = 0; idx < request.testCases().size(); idx++) {
                TestCase tc = request.testCases().get(idx);
                Path inputFile = runDir.resolve("input_" + idx + ".txt").toAbsolutePath();
                Files.writeString(inputFile, tc.input() == null ? "" : tc.input());
                Path outputFile = runDir.resolve("out_" + idx + ".txt").toAbsolutePath();

                List<String> cmd = runCommand(language, sourceFile, runDir);
                ProcessBuilder pb = new ProcessBuilder(cmd)
                        .directory(runDir.toFile())
                        .redirectErrorStream(true)
                        .redirectInput(inputFile.toFile())
                        // File redirect avoids the pipe-buffer deadlock that
                        // misjudged verbose programs as timeouts.
                        .redirectOutput(outputFile.toFile());
                Process proc = pb.start();

                if (!proc.waitFor(timeout, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                    results.add(new JudgeResult.CaseResult(idx, false, tc.expectedOutput(), "", "timeout"));
                    continue;
                }
                // ponytail: Java's Process API reports RSS only after exit on some JDKs; log if over limit post-hoc.
                int memLimitMb = request.memoryLimitMb();
                if (memLimitMb > 0) {
                    long memBytes = proc.info().totalMemorySize().orElse(0L);
                    if (memBytes > 0 && memBytes > (long) memLimitMb * 1024 * 1024) {
                        log.warn("Memory limit exceeded: {}MB > {}MB limit", memBytes / (1024 * 1024), memLimitMb);
                    }
                }
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
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Native judge execution failed for language {}", language, e);
            return new JudgeResult(0, request.testCases().size(), false, List.of());
        } finally {
            ExecIo.deleteTree(runDir);
        }
    }

    /** Returns null for interpreted languages; otherwise the compile argv. */
    private List<String> compileCommand(String language, Path sourceFile, Path runDir) {
        return switch (language) {
            case "c" -> List.of("gcc", "-O2", "-o", binary(runDir).toString(), sourceFile.toString());
            case "cpp" -> List.of("g++", "-O2", "-std=c++17", "-o", binary(runDir).toString(), sourceFile.toString());
            case "java" -> List.of("javac", "-d", runDir.toString(), sourceFile.toString());
            default -> null;
        };
    }

    /** Single-artifact languages may reuse a cached binary for identical sources. */
    private boolean cacheable(String language) {
        return language.equals("c") || language.equals("cpp");
    }

    /**
     * Compiles (or reuses the cached binary). Returns null on success, or a
     * non-null error marker on failure.
     */
    private String compileOrReuse(String language, String sourceCode, Path sourceFile,
                                  Path runDir, int timeoutSec) throws IOException, InterruptedException {
        if (!cacheable(language)) {
            List<String> cmd = compileCommand(language, sourceFile, runDir);
            if (cmd == null) return null;
            return runToCompletion(cmd, runDir, timeoutSec);
        }
        String key = CompileArtifactCache.keyFor(language, sourceCode);
        var cached = compileCache.get(key);
        if (cached.isPresent()) {
            Files.copy(cached.get(), binary(runDir), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        }
        List<String> cmd = compileCommand(language, sourceFile, runDir);
        String err = runToCompletion(cmd, runDir, timeoutSec);
        if (err == null) {
            compileCache.put(key, binary(runDir));
        }
        return err;
    }

    private List<String> runCommand(String language, Path sourceFile, Path runDir) {
        return switch (language) {
            case "c", "cpp" -> List.of(binary(runDir).toString());
            case "java" -> List.of("java", "-cp", runDir.toString(), mainClass(sourceFile));
            case "node" -> List.of("node", sourceFile.toString());
            default -> List.of("python", sourceFile.toString());
        };
    }

    private Path binary(Path runDir) {
        String name = System.getProperty("os.name", "").toLowerCase().contains("win") ? "program.exe" : "program";
        return runDir.resolve(name);
    }

    private String mainClass(Path sourceFile) {
        String name = sourceFile.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    /** Runs a fire-and-forget command; returns null on success or trimmed output on failure. */
    private String runToCompletion(List<String> cmd, Path dir, int timeoutSec)
            throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            return "compile_timeout";
        }
        if (proc.exitValue() == 0) return null;
        String out = ExecIo.readCapped(proc);
        return out == null ? "stdout_exceeded_1MB" : out;
    }

    private JudgeResult allFailed(JudgeRequest request, String code, String detail) {
        List<JudgeResult.CaseResult> results = new ArrayList<>();
        for (int i = 0; i < request.testCases().size(); i++) {
            results.add(new JudgeResult.CaseResult(i, false,
                    request.testCases().get(i).expectedOutput(), "", code));
        }
        return new JudgeResult(0, request.testCases().size(), false, results);
    }

    private String truncate(String s) {
        return s.length() <= 2000 ? s : s.substring(0, 2000) + "…";
    }

    /**
     * Live execution for the interactive console: compiles (if needed) then runs
     * the program feeding {@code stdin} and capturing combined stdout/stderr.
     * Callers (the player console) get back the merged stream so students can
     * experiment before submitting — mirroring JuiceMind's in-quiz runner.
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
        try {
            runDir = runDir.toAbsolutePath();
            String ext = switch (language) {
                case "c" -> ".c";
                case "cpp" -> ".cpp";
                case "java" -> ".java";
                case "node" -> ".js";
                default -> ".py";
            };
            String runFileName = language.equals("java") ? "Main" : "solution";
            Path sourceFile = runDir.resolve(runFileName + ext).toAbsolutePath();
            Files.writeString(sourceFile, request.sourceCode() == null ? "" : request.sourceCode());

            int timeout = request.timeoutSec() > 0 ? request.timeoutSec() : defaultTimeoutSec;

            String compileErr = compileOrReuse(language, request.sourceCode(), sourceFile, runDir, timeout);
            if (compileErr != null) {
                return new RunResult(false, "", compileErr, "compilation_error");
            }

            List<String> cmd = runCommand(language, sourceFile, runDir);
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(runDir.toFile())
                    .redirectErrorStream(true);
            Process proc = pb.start();

            // Feed stdin (best-effort; stdin may be empty for non-interactive programs).
            if (request.stdin() != null && !request.stdin().isEmpty()) {
                try (var os = proc.getOutputStream()) {
                    os.write(request.stdin().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // program may have exited before consuming all stdin
                }
            }

            if (!proc.waitFor(timeout, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return new RunResult(false, "", "", "timeout");
            }
            String output = ExecIo.readCapped(proc);
            if (output == null) {
                return new RunResult(false, "", "", "stdout_exceeded_1MB");
            }
            boolean ok = proc.exitValue() == 0;
            return new RunResult(ok, output, "", ok ? "ok" : "runtime_error");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Native run execution failed for language {}", language, e);
            return new RunResult(false, "", "", "io_error");
        } finally {
            ExecIo.deleteTree(runDir);
        }
    }
}
