package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotEnvAndDirsTest {

    private final DotEnvApplicationContextInitializer initializer =
            new DotEnvApplicationContextInitializer();

    @TempDir
    Path tmp;

    @Test
    void parsesKeysValuesCommentsAndQuotes() throws Exception {
        Path env = tmp.resolve(".env");
        Files.writeString(env, """
                # comment line
                SPRINTJUDGE_PORT=8090
                SPRINTJUDGE_MS_CLIENT_SECRET="secret with spaces"
                EMPTY=
                  PADDED_KEY = padded
                broken line without equals
                """);
        Map<String, Object> m = DotEnvApplicationContextInitializer.readDotEnv(env);
        assertEquals("8090", m.get("SPRINTJUDGE_PORT"));
        assertEquals("secret with spaces", m.get("SPRINTJUDGE_MS_CLIENT_SECRET"));
        assertEquals("", m.get("EMPTY"));
        assertEquals("padded", m.get("PADDED_KEY"));
        assertEquals(4, m.size());
    }

    @Test
    void missingFileYieldsEmptyMap() {
        assertTrue(DotEnvApplicationContextInitializer
                .readDotEnv(tmp.resolve("nope.env")).isEmpty());
    }

    @Test
    void appDirResolvesToExistingDirectory() {
        var dir = JarDirs.appDir();
        assertTrue(dir.isDirectory());
    }

    /**
     * Full initializer behavior: registers the sprintjudgeDotEnv source AFTER
     * the OS environment and exposes the resolved application directory.
     */
    @Test
    void initializeInsertsSourceAfterSystemEnvironment() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.setEnvironment(new StandardEnvironment());

        initializer.initialize(ctx);

        var source = ctx.getEnvironment().getPropertySources().get("sprintjudgeDotEnv");
        assertNotNull(source, "dotenv source must be registered");
        assertEquals(JarDirs.appDir().getAbsolutePath(),
                source.getProperty("sprintjudge.app-dir"));
        assertTrue(source.containsProperty("SPRINTJUDGE_APP_DIR"));

        // Precedence contract: OS env wins over .env, .env wins over YAML.
        var names = ctx.getEnvironment().getPropertySources().stream()
                .map(ps -> ps.getName()).toList();
        int sysPos = names.indexOf(names.stream()
                .filter(n -> n.toLowerCase().contains("systemenvironment")).findFirst().orElse(""));
        int dotPos = names.indexOf("sprintjudgeDotEnv");
        assertTrue(dotPos > sysPos, ".env must rank BELOW the OS environment");

        // Idempotent: a second run must not duplicate the source.
        initializer.initialize(ctx);
        assertEquals(1, names.stream().filter(n -> n.equals("sprintjudgeDotEnv")).count());
    }
}
