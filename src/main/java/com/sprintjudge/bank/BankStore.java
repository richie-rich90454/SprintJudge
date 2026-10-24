package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Classpath JSON question bank, loaded once at construction.
 * Only answer-free headers are retained: id, subject, unit, topic, scope,
 * format, difficulty, stem. Options, answers, explanations, and every other
 * field are dropped at load, so a 150000-question bank fits in a small heap.
 * Correct answers never leave this store.
 */
@Component
public class BankStore {

    private static final Logger log = LoggerFactory.getLogger(BankStore.class);

    private final List<Map<String, Object>> questions;
    private int skippedResources;

    /**
     * Loads every bank JSON document from the classpath.
     *
     * @throws IOException when a bank document cannot be read
     */
    public BankStore() throws IOException {
        this(new PathMatchingResourcePatternResolver(), new ObjectMapper());
    }

    BankStore(PathMatchingResourcePatternResolver resolver, ObjectMapper mapper) throws IOException {
        this(loadAll(resources(resolver), mapper));
    }

    private BankStore(Loaded loaded) {
        this.questions = List.copyOf(loaded.headers());
        this.skippedResources = loaded.skipped();
    }

    BankStore(List<Map<String, Object>> seed) {
        List<Map<String, Object>> headers = new ArrayList<>(seed.size());
        for (Map<String, Object> record : seed) {
            headers.add(header(record));
        }
        this.questions = List.copyOf(headers);
    }

    private static Resource[] resources(PathMatchingResourcePatternResolver resolver) throws IOException {
        return resolver.getResources("classpath*:bank/**/*.json");
    }

    record Loaded(List<Map<String, Object>> headers, int skipped) {}

    /**
     * Loads every readable bank file. A malformed or half-written file must
     * never take the whole application down: the strict validator reports it in
     * CI, while runtime keeps serving the other 99.9% of the library.
     */
    static Loaded loadAll(Resource[] resources, ObjectMapper mapper) {
        List<Map<String, Object>> all = new ArrayList<>();
        int skipped = 0;
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                all.addAll(recordsOf(mapper.readTree(in), mapper));
            } catch (IOException | RuntimeException e) {
                skipped++;
                log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
            }
        }
        if (skipped > 0) {
            log.warn("Bank loaded with {} unreadable file(s); run `make check-bank` to see details", skipped);
        }
        return new Loaded(all, skipped);
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

    /** Number of bank files skipped because they were unreadable at startup. */
    public int skippedResources() {
        return skippedResources;
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
