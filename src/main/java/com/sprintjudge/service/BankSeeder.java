package com.sprintjudge.service;

import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.service.ImportExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Seeds the bundled question library on first boot — ONLY when the bank is
 * completely empty, so existing installs are never touched.
 */
@Component
public class BankSeeder implements org.springframework.boot.ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BankSeeder.class);
    static final String BUNDLE = "seed/master-bundle.json";

    private final QuizRepository quizRepository;
    private final ImportExportService importExportService;

    public BankSeeder(QuizRepository quizRepository, ImportExportService importExportService) {
        this.quizRepository = quizRepository;
        this.importExportService = importExportService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!quizRepository.findAll().isEmpty()) {
            log.info("Question bank not empty - skipping bundled library seed");
            return;
        }
        try (var in = new ClassPathResource(BUNDLE).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int imported = importExportService.importAll(json, false);
            log.info("Seeded bundled question library: {} questions", imported);
        } catch (Exception e) {
            log.error("Bundled library seed failed", e);
        }
    }
}
