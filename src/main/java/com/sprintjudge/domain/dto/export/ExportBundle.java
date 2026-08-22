package com.sprintjudge.domain.dto.export;

import java.util.List;
import java.util.Map;

public record ExportBundle(
        String version,
        long exportedAt,
        List<QuizExport> quizzes,
        Map<String, String> adminSettings
) {
    public record QuizExport(
            String id, String title, String description,
            boolean template, List<QuestionExport> questions
    ) {}

    public record QuestionExport(
            String id, String type, String title, String description,
            int timeLimitSec, int pointsBase,
            Map<String, Object> config, List<String> languagesAllowed
    ) {}
}
