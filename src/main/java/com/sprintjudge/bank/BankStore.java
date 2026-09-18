package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Classpath JSON question bank, loaded once at construction.
 * Answer-free headers cross the wire: id, subject, unit, topic, scope,
 * format, difficulty, stem. Correct answers never leave this store.
 */
@Component
public class BankStore {

    private final List<Map<String, Object>> questions;

    /**
     * Loads every bank JSON document from the classpath.
     *
     * @throws IOException when a bank document cannot be read
     */
    public BankStore() throws IOException {
        this(new PathMatchingResourcePatternResolver(), new ObjectMapper());
    }

    BankStore(PathMatchingResourcePatternResolver resolver, ObjectMapper mapper) throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:bank/**/*.json")) {
            all.addAll(recordsOf(mapper.readTree(resource.getInputStream()), mapper));
        }
        this.questions = List.copyOf(all);
    }

    BankStore(List<Map<String, Object>> seed) {
        this.questions = List.copyOf(seed);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> recordsOf(JsonNode node, ObjectMapper mapper) {
        if (node.has("questions") && node.path("questions").isArray()) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (JsonNode entry : node.path("questions")) {
                batch.add(mapper.convertValue(entry, Map.class));
            }
            return batch;
        }
        return List.of(mapper.convertValue(node, Map.class));
    }

    /**
     * Answer-free headers matching every filter. Null scope and the
     * {@code everything} scope both mean all scopes; null or blank unit and
     * format both mean all values.
     *
     * @param subject required subject name
     * @param unit optional unit filter, blank means all
     * @param scope optional scope filter, blank or everything means all
     * @param format optional format filter, blank means all
     * @return matching answer-free headers
     */
    public List<Map<String, Object>> search(String subject, String unit, String scope, String format) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            if (!subject.equals(text(question, "subject"))) {
                continue;
            }
            if (!matchScope(question, scope)) {
                continue;
            }
            if (!matchText(text(question, "unit"), unit)) {
                continue;
            }
            if (!matchText(text(question, "format"), format)) {
                continue;
            }
            out.add(header(question));
        }
        return List.copyOf(out);
    }

    /**
     * Answer-free header for one question id.
     *
     * @param id question id
     * @return header, or empty when the id is unknown
     */
    public Optional<Map<String, Object>> headerById(String id) {
        for (Map<String, Object> question : questions) {
            if (id.equals(text(question, "id"))) {
                return Optional.of(header(question));
            }
        }
        return Optional.empty();
    }

    /**
     * Per-unit format and scope coverage for one subject.
     *
     * @param subject subject name
     * @return unit name to total, sorted formats, and sorted scopes
     */
    public Map<String, Map<String, Object>> coverage(String subject) {
        Map<String, List<Map<String, Object>>> byUnit = new TreeMap<>();
        for (Map<String, Object> question : questions) {
            if (!subject.equals(text(question, "subject"))) {
                continue;
            }
            byUnit.computeIfAbsent(text(question, "unit"), key -> new ArrayList<>()).add(question);
        }
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byUnit.entrySet()) {
            Set<String> formats = new TreeSet<>();
            Set<String> scopes = new TreeSet<>();
            for (Map<String, Object> question : entry.getValue()) {
                formats.add(text(question, "format"));
                scopes.add(text(question, "scope"));
            }
            Map<String, Object> unit = new LinkedHashMap<>();
            unit.put("total", entry.getValue().size());
            unit.put("formats", List.copyOf(formats));
            unit.put("scopes", List.copyOf(scopes));
            out.put(entry.getKey(), unit);
        }
        return out;
    }

    /**
     * Question totals per subject.
     *
     * @return subject name to total
     */
    public Map<String, Integer> volume() {
        Map<String, Integer> out = new TreeMap<>();
        for (Map<String, Object> question : questions) {
            out.merge(text(question, "subject"), 1, Integer::sum);
        }
        return out;
    }

    private static boolean matchScope(Map<String, Object> question, String scope) {
        if (scope == null || scope.isBlank() || "everything".equals(scope)) {
            return true;
        }
        return scope.equals(text(question, "scope"));
    }

    private static boolean matchText(String value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return filter.equals(value);
    }

    private static String text(Map<String, Object> question, String key) {
        return String.valueOf(question.get(key));
    }

    private static Map<String, Object> header(Map<String, Object> question) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", question.get("id"));
        out.put("subject", question.get("subject"));
        out.put("unit", question.get("unit"));
        out.put("topic", question.get("topic"));
        out.put("scope", question.get("scope"));
        out.put("format", question.get("format"));
        out.put("difficulty", question.get("difficulty"));
        out.put("stem", question.get("stem"));
        return out;
    }
}
