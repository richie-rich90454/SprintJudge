package com.sprintjudge.service;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seeds the bundled question library on first boot — ONLY when the bank is
 * completely empty, so existing installs are never touched.
 */
@Component
public class BankSeeder implements org.springframework.boot.ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BankSeeder.class);
    static final String BUNDLE = "seed/master-bundle.json";
    /** Endpoint abuse cap is per import; the bundled seed ships in full-size chunks. */
    static final int SEED_CHUNK_QUIZZES = 200;

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ImportExportService importExportService;

    public BankSeeder(QuizRepository quizRepository, QuestionRepository questionRepository,
                      ImportExportService importExportService) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.importExportService = importExportService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        boolean force = Boolean.getBoolean("sprintjudge.seed.force");
        if (!force && quizRepository.count() > 0) {
            log.info("Question bank not empty - skipping bundled library seed (set -Dsprintjudge.seed.force=true to re-seed)");
            return;
        }
        if (force) {
            // Wipe once up front: per-chunk replace would erase earlier chunks.
            for (Quiz q : quizRepository.findAll()) {
                questionRepository.deleteByQuiz(q.id());
                quizRepository.delete(q.id());
            }
        }
        try (var in = new ClassPathResource(BUNDLE).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // ponytail: chunked import keeps the endpoint abuse cap intact
            ExportBundle bundle = Json.read(json, ExportBundle.class);
            int imported = 0;
            for (List<ExportBundle.QuizExport> slice : partition(bundle.quizzes())) {
                imported += importExportService.importAll(chunkJson(bundle, slice), false);
            }
            log.info("Seeded bundled question library: {} questions", imported);
        } catch (Exception e) {
            log.error("Bundled library seed failed", e);
        }
    }

    /** Split into full-size slices so every import honors the endpoint abuse cap. */
    static List<List<ExportBundle.QuizExport>> partition(List<ExportBundle.QuizExport> all) {
        List<List<ExportBundle.QuizExport>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += SEED_CHUNK_QUIZZES) {
            chunks.add(all.subList(i, Math.min(i + SEED_CHUNK_QUIZZES, all.size())));
        }
        return chunks;
    }

    private static String chunkJson(ExportBundle bundle, List<ExportBundle.QuizExport> slice) {
        return Json.write(new ExportBundle(
                bundle.version(), bundle.exportedAt(), slice, bundle.adminSettings()));
    }
}
