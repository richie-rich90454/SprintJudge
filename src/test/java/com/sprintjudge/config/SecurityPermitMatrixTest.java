package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the real application context once and pins the HTTP security matrix:
 * public SPA deep links serve the shell logged-out, admin/ops surfaces bounce
 * anonymous callers to the login form. Runs against an isolated temp database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityPermitMatrixTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> tempDir.resolve("matrix.db").toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void joinLinkServesShellLoggedOut() throws Exception {
        mvc.perform(get("/j/123456"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void spaRoutesServeShellLoggedOut() throws Exception {
        for (String path : new String[]{"/join", "/play", "/host", "/results", "/solo", "/explore"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void publicApiServesLoggedOut() throws Exception {
        mvc.perform(get("/api/public/quizzes")).andExpect(status().isOk());
    }

    @Test
    void adminApiRedirectsAnonymousToLogin() throws Exception {
        mvc.perform(get("/api/admin/quizzes"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/login")));
    }

    @Test
    void adminPagesRedirectAnonymousToLogin() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/login")));
    }

    @Test
    void healthIsPublicButPrometheusIsNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isFound());
    }
}
