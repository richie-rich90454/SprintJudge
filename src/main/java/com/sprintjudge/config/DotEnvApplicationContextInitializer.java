package com.sprintjudge.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads KEY=VALUE pairs from a .env file into the Spring Environment.
 *
 * <p>Lookup order: {@code <jar-folder>/.env} first, then {@code ./env/.env},
 * then {@code ./.env}. The source is inserted directly AFTER the OS
 * environment, so real environment variables always win over .env, while .env
 * overrides YAML defaults. Values may be wrapped in single or double quotes;
 * lines starting with # are comments.
 *
 * <p>Registered via {@code spring.factories} (context.initializer.classes) —
 * the supported early hook in Boot 4, replacing the deprecated
 * EnvironmentPostProcessor SPI.
 */
public class DotEnvApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String SOURCE_NAME = "sprintjudgeDotEnv";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        if (sources.contains(SOURCE_NAME)) return;

        Map<String, Object> values = new HashMap<>();
        File appDir = JarDirs.appDir();
        Path[] candidates = {
                new File(appDir, ".env").toPath(),
                Path.of(System.getProperty("user.dir"), "env", ".env"),
                Path.of(System.getProperty("user.dir"), ".env"),
        };
        boolean found = false;
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                values.putAll(readDotEnv(p));
                System.out.println("[SprintJudge] .env loaded from: " + p);
                found = true;
                break;   // first .env wins
            }
        }
        if (!found) System.out.println("[SprintJudge] No .env file found — checked: "
                + String.join(", ", java.util.Arrays.stream(candidates).map(Path::toString).toList()));
        // Expose the resolved app dir for path defaults (e.g., SQLite location).
        values.putIfAbsent("SPRINTJUDGE_APP_DIR", appDir.getAbsolutePath());
        values.putIfAbsent("sprintjudge.app-dir", appDir.getAbsolutePath());

        String sysName = null;
        for (var ps : sources) {
            if (ps.getName().toLowerCase().contains("systemenvironment")) { sysName = ps.getName(); break; }
        }
        MapPropertySource source = new MapPropertySource(SOURCE_NAME, values);
        if (sysName != null) sources.addAfter(sysName, source);
        else sources.addLast(source);
    }

    /** Parses one .env file: KEY=VALUE, # comments, optional surrounding quotes. */
    public static Map<String, Object> readDotEnv(Path file) {
        Map<String, Object> out = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).strip();
                String value = stripQuotes(line.substring(eq + 1).strip());
                out.putIfAbsent(key, value);
            }
        } catch (Exception ignored) {
            // A malformed .env must never block boot.
        }
        return out;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
