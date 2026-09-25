package com.sprintjudge.controller;

import com.sprintjudge.repository.Tables;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.jooq.DSLContext;
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
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calibration HTTP surface through the real filter chain: anonymous callers get the
 * machine-readable 401 inherited from /api/admin/**, admins can read persisted rows
 * and trigger a full recalibration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CalibrationControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> tempDir.resolve("calibration.db").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    DSLContext dsl;

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
    void anonymousGetIs401() throws Exception {
        mvc.perform(get("/api/admin/bank/calibration/some-q"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRunIs401() throws Exception {
        mvc.perform(post("/api/admin/bank/calibration/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownIdIs404() throws Exception {
        mvc.perform(get("/api/admin/bank/calibration/no-such-question").session(adminSession()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankIdIs400() throws Exception {
        mvc.perform(get("/api/admin/bank/calibration/%20").session(adminSession()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runCalibratesAndPersists() throws Exception {
        String qid = "calib-q-" + UUID.randomUUID();
        String quizId = "quiz-" + qid;
        String sessId = "sess-" + qid;
        dsl.insertInto(Tables.QUIZZES)
                .columns(Tables.QUIZZES_ID, Tables.QUIZZES_TITLE)
                .values(quizId, "calibration test quiz")
                .execute();
        dsl.insertInto(Tables.QUESTIONS)
                .columns(Tables.QUESTIONS_ID, Tables.QUESTIONS_QUIZ_ID, Tables.QUESTIONS_TITLE)
                .values(qid, quizId, "calibration test question")
                .execute();
        dsl.insertInto(Tables.GAME_SESSIONS)
                .columns(Tables.SESS_ID, Tables.SESS_QUIZ_ID)
                .values(sessId, quizId)
                .execute();
        long now = Instant.now().getEpochSecond();
        boolean[] correct = {true, true, false, false};
        for (int i = 0; i < correct.length; i++) {
            dsl.insertInto(Tables.SUBMISSIONS)
                    .columns(Tables.SUB_ID, Tables.SUB_SESS, Tables.SUB_QUESTION, Tables.SUB_PNAME,
                            Tables.SUB_PUUID, Tables.SUB_DATA, Tables.SUB_SCORE, Tables.SUB_CORRECT,
                            Tables.SUB_LOG, Tables.SUB_ATTEMPTS, Tables.SUB_AT)
                    .values(UUID.randomUUID().toString(), sessId, qid, "p" + i, "uuid-" + i,
                            "{}", correct[i] ? 1 : 0, correct[i], "", 1, now + i)
                    .execute();
        }

        mvc.perform(post("/api/admin/bank/calibration/run").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calibrated").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/admin/bank/calibration/{id}", qid).session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(qid))
                .andExpect(jsonPath("$.pValue").value(closeTo(0.5, 0.0001)))
                .andExpect(jsonPath("$.discrimination").value(closeTo(1.0, 0.0001)))
                .andExpect(jsonPath("$.attempts").value(4));
    }
}
