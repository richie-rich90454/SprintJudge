package com.sprintjudge.service;

import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Turns validated bank records into a normal playable quiz. The bank stays an
 * authoring surface; the generated quiz is a first-class room asset with the
 * same lifecycle as any teacher-created set.
 */
@Service
public class BankQuizBuilder {

    private static final Set<String> MCQ_FORMATS = Set.of(
            "CONCEPT_MCQ", "STIMULUS_MCQ", "SOURCE_BASED", "ERROR_ANALYSIS",
            "DATA_INTERPRETATION", "CONCEPTUAL_REASONING", "RHETORICAL_ANALYSIS",
            "SYNTHESIS", "DIAGRAM", "CODE_TRACING", "BUG_SPOTTING", "CODE_COMPLETION", "MCQ");

    private static final Map<Integer, List<String>> CSA_UNITS = Map.of(
            1, List.of("Unit 1", "Unit 2"),
            2, List.of("Unit 3", "Unit 4"),
            3, List.of("Unit 5"),
            4, List.of("Unit 6", "Unit 7", "Unit 9", "Unit 10"));

    private static final Map<Integer, String> CSA_TITLES = Map.of(
            1, "CSA 1: Using Objects and Methods",
            2, "CSA 2: Selection and Iteration",
            3, "CSA 3: Class Creation",
            4, "CSA 4: Data Collections",
            5, "Mixed CSA: Units 1 to 4");

    private final BankRecordSource records;
    private final ImportExportService importExportService;
    private final String codingBundle;
    private volatile ExportBundle.QuestionExport codingQuestion;

    @Autowired
    public BankQuizBuilder(BankRecordSource records, ImportExportService importExportService) {
        this(records, importExportService, "seed/master-bundle.json");
    }

    BankQuizBuilder(BankRecordSource records, ImportExportService importExportService,
                    String codingBundle) {
        this.records = records;
        this.importExportService = importExportService;
        this.codingBundle = codingBundle;
    }

    public record BuildRequest(String subject, String unit, int csaUnit, int count,
                               String title, long seed, boolean includeCoding) {}

    public record BuildResult(String quizId, int questions, int skipped) {}

    public BuildResult build(BuildRequest request) {
        int count = Math.max(1, Math.min(30, request.count() <= 0 ? 12 : request.count()));
        boolean csa = request.csaUnit() >= 1 && request.csaUnit() <= 5;
        String subject = csa
                ? "JAVA_PROGRAMMING"
                : blank(request.subject()) ? "JAVA_PROGRAMMING" : request.subject().trim();
        Set<String> playableFormats = union(MCQ_FORMATS, Set.of("MULTI_SELECT", "NUMERIC",
                "FORMULA_APPLICATION", "CALCULATION_WITH_UNITS", "NUMERIC_CALCULATION"));
        Random random = new Random(request.seed() == 0 ? 2026L + request.csaUnit() : request.seed());
        ExportBundle.QuestionExport coding = request.includeCoding() ? codingQuestion(subject) : null;
        int bankTarget = coding == null ? count : Math.max(1, count - 1);

        List<Map<String, Object>> candidates = new ArrayList<>();
        if (request.csaUnit() == 5) {
            int perUnit = Math.max(1, (bankTarget + 3) / 4);
            for (int csaUnit = 1; csaUnit <= 4; csaUnit++) {
                List<Map<String, Object>> group = new ArrayList<>();
                for (String unit : CSA_UNITS.get(csaUnit)) {
                    group.addAll(records.playable(subject, unit, playableFormats));
                }
                Collections.shuffle(group, random);
                for (Map<String, Object> record : group.subList(0, Math.min(perUnit, group.size()))) {
                    Map<String, Object> tagged = new LinkedHashMap<>(record);
                    tagged.put("csaUnit", "CSA " + csaUnit);
                    candidates.add(tagged);
                }
            }
        } else {
            List<String> units = request.csaUnit() >= 1 && request.csaUnit() <= 4
                    ? CSA_UNITS.get(request.csaUnit())
                    : blank(request.unit()) ? List.of("") : List.of(request.unit().trim());
            for (String unit : units) {
                List<Map<String, Object>> unitRecords = records.playable(
                        subject, unit.isBlank() ? null : unit, playableFormats);
                Collections.shuffle(unitRecords, random);
                candidates.addAll(unitRecords);
            }
        }
        Collections.shuffle(candidates, random);
        int skipped = 0;

        List<ExportBundle.QuestionExport> questions = new ArrayList<>();
        for (Map<String, Object> record : candidates) {
            if (questions.size() >= bankTarget) break;
            ExportBundle.QuestionExport built = toQuestion(record, request.csaUnit());
            if (built == null) {
                skipped++;
                continue;
            }
            questions.add(built);
        }
        if (coding != null) {
            questions.add(new ExportBundle.QuestionExport(
                    Ids.uuid(), coding.type(), coding.title(), coding.description(),
                    coding.timeLimitSec(), coding.pointsBase(), coding.config(), coding.languagesAllowed()));
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No playable questions found for that bank selection");
        }

        String title = blank(request.title())
                ? csa ? CSA_TITLES.get(request.csaUnit()) : "Java practice"
                : request.title().trim();
        String quizId = Ids.uuid();
        ExportBundle bundle = new ExportBundle(
                "1.0", Instant.now().getEpochSecond(),
                List.of(new ExportBundle.QuizExport(quizId, title,
                        "Built from the question bank for classroom play.", false, questions)),
                Map.of());
        importExportService.importAll(Json.write(bundle), false);
        return new BuildResult(quizId, questions.size(), skipped);
    }

    @SuppressWarnings("unchecked")
    ExportBundle.QuestionExport toQuestion(Map<String, Object> record, int csaUnit) {
        String format = text(record, "format").toUpperCase(Locale.ROOT);
        String stem = formatStem(text(record, "stem"));
        String explanation = text(record, "explanation");
        if (stem.isBlank() || stem.length() > 4000 || explanation.isBlank()) return null;
        String recordUnit = text(record, "csaUnit");
        if (record.get("options") instanceof List<?> rawOptions && !rawOptions.isEmpty()) {
            List<Map<String, Object>> options = (List<Map<String, Object>>) rawOptions;
            Map<String, Object> answer = asMap(record.get("answer"));
            if (MCQ_FORMATS.contains(format)) {
                int index = optionIndex(options, text(answer, "correctId"));
                if (index < 0) return null;
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("options", options.stream().map(o -> text(o, "text")).toList());
                config.put("correctIndex", index);
                config.put("explanation", explanation);
                config.put("hint", text(record, "hint"));
                config.put("optionMeta", optionMeta(options));
                config.put("topic", text(record, "topic"));
                config.put("unit", text(record, "unit"));
                if (!recordUnit.isBlank()) config.put("csaUnit", recordUnit);
                else if (csaUnit >= 1 && csaUnit <= 4) config.put("csaUnit", "CSA " + csaUnit);
                return new ExportBundle.QuestionExport(Ids.uuid(), "MCQ", titleFor(record),
                        stem, 60, pointsFor(format), config, null);
            }
            if ("MULTI_SELECT".equals(format)) {
                List<Integer> indices = correctIndices(options, answer.get("correctIds"));
                if (indices.size() < 2) return null;
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("options", options.stream().map(o -> text(o, "text")).toList());
                config.put("correctIndices", indices);
                config.put("explanation", explanation);
                config.put("hint", text(record, "hint"));
                config.put("optionMeta", optionMeta(options));
                config.put("topic", text(record, "topic"));
                config.put("unit", text(record, "unit"));
                if (!recordUnit.isBlank()) config.put("csaUnit", recordUnit);
                else if (csaUnit >= 1 && csaUnit <= 4) config.put("csaUnit", "CSA " + csaUnit);
                return new ExportBundle.QuestionExport(Ids.uuid(), "MULTIPLE_SELECT", titleFor(record),
                        stem, 90, 200, config, null);
            }
        }
        if (Set.of("NUMERIC", "FORMULA_APPLICATION", "CALCULATION_WITH_UNITS",
                "NUMERIC_CALCULATION").contains(format)) {
            Map<String, Object> answer = asMap(record.get("answer"));
            double value = number(answer.get("value"), number(answer.get("answer"), Double.NaN));
            if (Double.isNaN(value)) return null;
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("answer", value);
            config.put("tolerance", number(answer.get("tolerance"), 0.0));
            config.put("explanation", explanation);
            config.put("hint", text(record, "hint"));
            config.put("topic", text(record, "topic"));
            config.put("unit", text(record, "unit"));
            if (!recordUnit.isBlank()) config.put("csaUnit", recordUnit);
            else if (csaUnit >= 1 && csaUnit <= 4) config.put("csaUnit", "CSA " + csaUnit);
            return new ExportBundle.QuestionExport(Ids.uuid(), "NUMERIC", titleFor(record),
                    stem, 90, 200, config, null);
        }
        return null;
    }

    private synchronized ExportBundle.QuestionExport codingQuestion(String subject) {
        if (!"JAVA_PROGRAMMING".equals(subject)) return null;
        if (codingQuestion != null) return codingQuestion;
        try (var in = new ClassPathResource(codingBundle).getInputStream()) {
            ExportBundle bundle = Json.read(new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8), ExportBundle.class);
            ExportBundle.QuestionExport fallback = null;
            for (ExportBundle.QuizExport quiz : bundle.quizzes()) {
                boolean preferred = isPreferred(quiz.title());
                for (ExportBundle.QuestionExport question : quiz.questions()) {
                    if (!"OJ_FULL".equals(question.type())) continue;
                    if (preferred) {
                        codingQuestion = question;
                        return question;
                    }
                    if (fallback == null) fallback = question;
                }
            }
            codingQuestion = fallback;
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.HashSet<String> out = new java.util.HashSet<>(a);
        out.addAll(b);
        return out;
    }

    static boolean isPreferred(String title) {
        return title != null
                && (title.contains("Arrays") || title.contains("Lists") || title.contains("Recursion"));
    }

    static int pointsFor(String format) {
        if ("CODE_TRACING".equals(format) || "BUG_SPOTTING".equals(format)) return 150;
        if ("CODE_COMPLETION".equals(format)) return 200;
        return 100;
    }

    /**
     * Bank stems carry a prose line followed by raw code. Fenced code renders
     * with line numbers on the teacher stage; raw code does not. Never wrap a
     * single-line inline snippet, and never touch a stem already fenced.
     */
    static String formatStem(String stem) {
        if (stem == null || stem.contains("```")) return stem == null ? "" : stem;
        int newline = stem.indexOf('\n');
        if (newline < 0) return stem;
        String prose = stem.substring(0, newline).trim();
        String rest = stem.substring(newline + 1).trim();
        boolean code = rest.contains("{") || rest.contains(";") || rest.contains("class ");
        if (!code) return stem;
        return prose + "\n\n```java\n" + rest + "\n```";
    }

    private static String titleFor(Map<String, Object> record) {
        String topic = text(record, "topic");
        return topic.isBlank() ? "Bank question" : topic;
    }

    private static List<Map<String, Object>> optionMeta(List<Map<String, Object>> options) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> option : options) {
            if (text(option, "misconception").isBlank()) continue;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", text(option, "id"));
            meta.put("misconception", text(option, "misconception"));
            out.add(meta);
        }
        return out;
    }

    private static int optionIndex(List<Map<String, Object>> options, String id) {
        for (int i = 0; i < options.size(); i++) {
            if (id.equals(text(options.get(i), "id"))) return i;
        }
        return -1;
    }

    private static List<Integer> correctIndices(List<Map<String, Object>> options, Object ids) {
        List<Integer> out = new ArrayList<>();
        if (ids instanceof List<?> list) {
            for (Object id : list) {
                int index = optionIndex(options, String.valueOf(id));
                if (index >= 0) out.add(index);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
