package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Full bank records for the quiz builder. Headers stay in {@link BankStore};
 * this source reads the selected subject/unit on demand and caches only that
 * slice, so a 200MB library never becomes a 200MB heap residency.
 */
@Component
public class BankRecordSource {

    private static final Logger log = LoggerFactory.getLogger(BankRecordSource.class);
    private static final String BANK_GLOB = "classpath*:bank/**/*.json";

    private final Resource[] resources;
    private final ObjectMapper mapper;
    private final Map<String, List<Map<String, Object>>> cache = new ConcurrentHashMap<>();
    private volatile Set<String> subjectsCache;

    public BankRecordSource() throws IOException {
        this(new PathMatchingResourcePatternResolver().getResources(BANK_GLOB), new ObjectMapper());
    }

    public BankRecordSource(Resource[] resources, ObjectMapper mapper) {
        this.resources = resources;
        this.mapper = mapper;
    }

    /**
     * Full records for a subject, optionally narrowed to a unit. Unreadable
     * files are skipped with a log line; strict validation lives in CI.
     */
    public List<Map<String, Object>> records(String subject, String unit) {
        String key = subject + "|" + (unit == null ? "" : unit);
        return cache.computeIfAbsent(key, ignored -> load(subject, unit));
    }

    private List<Map<String, Object>> load(String subject, String unit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                for (Map<String, Object> record : BankStore.recordsOf(mapper.readTree(in), mapper)) {
                    if (!subject.equals(text(record, "subject"))) continue;
                    if (unit != null && !unit.equals(text(record, "unit"))) continue;
                    out.add(record);
                }
            } catch (IOException | RuntimeException e) {
                log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Distinct subject names present in the bank. Scans every classpath bank
     * resource once and caches the result.
     */
    public Set<String> subjects() {
        Set<String> cached = subjectsCache;
        if (cached != null) return cached;
        Set<String> out = new java.util.TreeSet<>();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                for (Map<String, Object> record : BankStore.recordsOf(mapper.readTree(in), mapper)) {
                    String subject = text(record, "subject");
                    if (!subject.isBlank()) out.add(subject);
                }
            } catch (IOException | RuntimeException e) {
                log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
            }
        }
        Set<String> copy = Set.copyOf(out);
        subjectsCache = copy;
        return copy;
    }

    /** Playable records for a unit, filtered to the mapped question formats. */
    public List<Map<String, Object>> playable(String subject, String unit, Set<String> formats) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> record : records(subject, unit)) {
            String format = text(record, "format").toUpperCase(Locale.ROOT);
            if (formats.contains(format)) out.add(record);
        }
        return out;
    }

    private static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
