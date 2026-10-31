package com.sprintjudge.controller;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scoring signal through the real stack: public route, copy-deck strings,
 * tier bands, and validation errors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ScoringControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> tempDir.resolve("scoring.db").toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void fluent() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal").value("FLUENT"))
                .andExpect(jsonPath("$.copy").value("You know this cold."))
                .andExpect(jsonPath("$.tier").value("Practicing"));
    }

    @Test
    void guessing() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal").value("GUESSING"))
                .andExpect(jsonPath("$.copy")
                        .value("You are fast but guessing on this topic. Try slowing down."));
    }

    @Test
    void careful() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "30")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal").value("CAREFUL"))
                .andExpect(jsonPath("$.copy").value("You know it, but fluency needs work."));
    }

    @Test
    void struggling() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "30")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signal").value("STRUGGLING"))
                .andExpect(jsonPath("$.copy").value("This topic needs reteaching. Try practice mode."));
    }

    @Test
    void tierBands() throws Exception {
        assertTier(95, "Gold");
        assertTier(85, "Gold");
        assertTier(70, "Silver");
        assertTier(55, "Bronze");
        assertTier(40, "Rising");
        assertTier(39.9, "Practicing");
    }

    private void assertTier(double sjs, String tier) throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.9")
                        .param("sjs", String.valueOf(sjs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value(tier));
    }

    @Test
    void negativeMedianSecondsIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "-1")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeQuestionMedianIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "-1")
                        .param("accuracy", "0.9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accuracyAboveOneIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18")
                        .param("accuracy", "1.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accuracyBelowZeroIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18")
                        .param("accuracy", "-0.1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAccuracyIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("questionMedian", "18"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMedianSecondsIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("questionMedian", "18")
                        .param("accuracy", "0.9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingQuestionMedianIs400() throws Exception {
        mvc.perform(get("/api/public/scoring/signal")
                        .param("medianSeconds", "10")
                        .param("accuracy", "0.9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void copyFallback() {
        assertEquals("This topic needs reteaching. Try practice mode.",
                ScoringController.copyOf("BOGUS"));
    }
}
