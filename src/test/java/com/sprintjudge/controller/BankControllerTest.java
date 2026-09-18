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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bank HTTP surface through the real filter chain: anonymous callers get the
 * machine-readable 401 inherited from /api/admin/**, admins get headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BankControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> tempDir.resolve("bank.db").toString());
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
    void anonymousSearchIs401() throws Exception {
        mvc.perform(get("/api/admin/bank/search").param("subject", "COMPUTING_FOUNDATIONS"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousQuestionIs401() throws Exception {
        mvc.perform(get("/api/admin/bank/question/cf-2.1-0001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchHappyPath() throws Exception {
        mvc.perform(get("/api/admin/bank/search")
                        .session(adminSession())
                        .param("subject", "COMPUTING_FOUNDATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].answer").doesNotExist());
    }

    @Test
    void searchWithEverythingScope() throws Exception {
        mvc.perform(get("/api/admin/bank/search")
                        .session(adminSession())
                        .param("subject", "JAVA_PROGRAMMING")
                        .param("scope", "everything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void searchWithFilters() throws Exception {
        mvc.perform(get("/api/admin/bank/search")
                        .session(adminSession())
                        .param("subject", "COMPUTING_FOUNDATIONS")
                        .param("unit", "Domain 2")
                        .param("scope", "core-scope")
                        .param("format", "CONCEPT_MCQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void searchNoMatchIsEmpty() throws Exception {
        mvc.perform(get("/api/admin/bank/search")
                        .session(adminSession())
                        .param("subject", "COMPUTING_FOUNDATIONS")
                        .param("unit", "Nope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchMissingSubjectIs400() throws Exception {
        mvc.perform(get("/api/admin/bank/search").session(adminSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBlankSubjectIs400() throws Exception {
        mvc.perform(get("/api/admin/bank/search")
                        .session(adminSession())
                        .param("subject", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void questionHit() throws Exception {
        mvc.perform(get("/api/admin/bank/question/cf-2.1-0001")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("COMPUTING_FOUNDATIONS"))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void questionMissIs404() throws Exception {
        mvc.perform(get("/api/admin/bank/question/nope").session(adminSession()))
                .andExpect(status().isNotFound());
    }

    @Test
    void questionBlankIdIs400() throws Exception {
        mvc.perform(get("/api/admin/bank/question/%20").session(adminSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coverageHappyPath() throws Exception {
        mvc.perform(get("/api/admin/bank/coverage/COMPUTING_FOUNDATIONS")
                        .session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Domain 2'].total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$['Domain 2'].formats").value(hasItem("CONCEPT_MCQ")))
                .andExpect(jsonPath("$['Domain 2'].scopes").value(hasItem("core-scope")));
    }

    @Test
    void coverageBlankSubjectIs400() throws Exception {
        mvc.perform(get("/api/admin/bank/coverage/%20").session(adminSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void volumeHappyPath() throws Exception {
        mvc.perform(get("/api/admin/bank/volume").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.COMPUTING_FOUNDATIONS").value(greaterThan(0)))
                .andExpect(jsonPath("$.JAVA_PROGRAMMING").value(greaterThan(0)));
    }
}
