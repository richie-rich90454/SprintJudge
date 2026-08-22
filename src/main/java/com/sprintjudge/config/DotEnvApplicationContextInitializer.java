package com.sprintjudge.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
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
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains("sprintjudgeDotEnv")) return;

        Map<String, Object> values = new HashMap<>();
        File appDir = JarDirs.appDir();
        Path[] candidates = {
                new File(appDir, ".env").toPath(),
                Path.of(System.getProperty("user.dir"), "env", ".env"),
                Path.of(System.getProperty("user.dir"), ".env"),
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                values.putAll(readDotEnv(p));
                break;   // first .env wins
            }
        }
        // Expose the resolved app dir for path defaults (e.g., SQLite location).
        values.putIfAbsent("SPRINTJUDGE_APP_DIR", appDir.getAbsolutePath());
        values.putIfAbsent("sprintjudge.app-dir", appDir.getAbsolutePath());

        MutablePropertySources sources = environment.getPropertySources();
        MapPropertySource source = new MapPropertySource("sprintjudgeDotEnv", values);
        String sysName = systemName(sources);
        if (sysName != null) sources.addAfter(sysName, source);
        else sources.addLast(source);
    }

    private String systemName(MutablePropertySources sources) {
        for (var ps : sources) {
            if (ps.getName().toLowerCase().contains("systemenvironment")) return ps.getName();
        }
        return null;
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
