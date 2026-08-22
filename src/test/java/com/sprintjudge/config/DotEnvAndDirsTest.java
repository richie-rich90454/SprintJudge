package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotEnvAndDirsTest {

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
        Map<String, Object> m = DotEnvEnvironmentPostProcessor.readDotEnv(env);
        assertEquals("8090", m.get("SPRINTJUDGE_PORT"));
        assertEquals("secret with spaces", m.get("SPRINTJUDGE_MS_CLIENT_SECRET"));
        assertEquals("", m.get("EMPTY"));
        assertEquals("padded", m.get("PADDED_KEY"));
        assertEquals(4, m.size());
    }

    @Test
    void missingFileYieldsEmptyMap() {
        assertTrue(DotEnvEnvironmentPostProcessor
                .readDotEnv(tmp.resolve("nope.env")).isEmpty());
    }

    @Test
    void appDirResolvesToExistingDirectory() {
        var dir = JarDirs.appDir();
        assertTrue(dir.isDirectory());
    }
}
