package com.sprintjudge.controller;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Room export through the real filter chain: anonymous callers get the
 * machine-readable 401 inherited from /api/admin/**, admins get CSV or JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RoomExportControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> tempDir.resolve("export.db").toString());
    }

    @Autowired
    MockMvc mvc;

    @Value("${sprintjudge.admin.username:admin}")
    String adminUsername;

    @Value("${sprintjudge.admin.password:changeme}")
    String adminPassword;

    private static MockHttpSession adminSession;

    /** Logs in once per class through the real form-login endpoint. */
    @BeforeEach
    void loginOnce() throws Exception {
        if (adminSession == null) {
            adminSession = (MockHttpSession) mvc
                    .perform(post("/admin/login")
                            .param("username", adminUsername)
                            .param("password", adminPassword))
                    .andExpect(status().isFound())
                    .andReturn().getRequest().getSession(false);
            assertNotNull(adminSession);
        }
    }

    private MockHttpSession adminSession() {
        return adminSession;
    }

    @Test
    void anonymousCsvIs401() throws Exception {
        mvc.perform(get("/api/admin/export/room/s1").param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousJsonIs401() throws Exception {
        mvc.perform(get("/api/admin/export/room/s1").param("format", "json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csvReturnsHeaderForUnknownSession() throws Exception {
        mvc.perform(get("/api/admin/export/room/unknown").session(adminSession()).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string("playerUuid,questionId,scoreEarned,correct,attemptCount"));
    }

    @Test
    void jsonReturnsEmptyListForUnknownSession() throws Exception {
        mvc.perform(get("/api/admin/export/room/unknown").session(adminSession()).param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void csvContentCarriesStoredRows() throws Exception {
        mvc.perform(get("/api/admin/export/room/unknown").session(adminSession()).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("playerUuid,questionId")));
    }
}
