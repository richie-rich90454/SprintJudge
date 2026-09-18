package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStoreTest {

    private static Map<String, Object> question(String id, String subject, String unit,
                                                String topic, String scope, String format,
                                                double difficulty) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("subject", subject);
        out.put("unit", unit);
        out.put("topic", topic);
        out.put("scope", scope);
        out.put("format", format);
        out.put("difficulty", difficulty);
        out.put("stem", "secret stem");
        out.put("answer", Map.of("correctId", "A"));
        return out;
    }

    private static BankStore store() {
        return new BankStore(List.of(
                question("a1", "MATH", "Unit 1", "Algebra", "core-scope", "CONCEPT_MCQ", 0.7),
                question("a2", "MATH", "Unit 1", "Geometry", "legacy-scope", "NUMERIC", 0.5),
                question("a3", "MATH", "Unit 2", "Algebra", "core-scope", "NUMERIC", 0.6),
                question("b1", "PHYS", "Unit 1", "Motion", "core-scope", "CONCEPT_MCQ", 0.8)));
    }

    @Test
    void searchMatchesSubjectOnly() {
        assertEquals(3, store().search("MATH", null, null, null).size());
    }

    @Test
    void searchBlankFiltersMeanAll() {
        assertEquals(3, store().search("MATH", "  ", "", " ").size());
    }

    @Test
    void searchFiltersByUnit() {
        assertEquals(2, store().search("MATH", "Unit 1", null, null).size());
    }

    @Test
    void searchFiltersByFormat() {
        assertEquals(1, store().search("MATH", null, null, "CONCEPT_MCQ").size());
    }

    @Test
    void searchFiltersByScope() {
        assertEquals(1, store().search("MATH", null, "legacy-scope", null).size());
    }

    @Test
    void searchEverythingScopeMeansAll() {
        assertEquals(3, store().search("MATH", null, "everything", null).size());
    }

    @Test
    void searchCombinesAllFilters() {
        var got = store().search("MATH", "Unit 1", "core-scope", "CONCEPT_MCQ");
        assertEquals(1, got.size());
        assertEquals("a1", got.get(0).get("id"));
    }

    @Test
    void searchNoMatchReturnsEmpty() {
        assertTrue(store().search("MATH", "Unit 9", null, null).isEmpty());
        assertTrue(store().search("MATH", null, "current-scope", null).isEmpty());
        assertTrue(store().search("MATH", null, null, "ORDERING").isEmpty());
        assertTrue(store().search("NOPE", null, null, null).isEmpty());
    }

    @Test
    void searchHeadersAreAnswerFree() {
        var got = store().search("PHYS", null, null, null);
        assertEquals(1, got.size());
        assertEquals(Set.of("id", "subject", "unit", "topic", "scope", "format", "difficulty", "stem"),
                got.get(0).keySet());
    }

    @Test
    void headerByIdHit() {
        Optional<Map<String, Object>> got = store().headerById("a2");
        assertTrue(got.isPresent());
        assertEquals("Geometry", got.get().get("topic"));
        assertEquals(0.5, got.get().get("difficulty"));
        assertEquals(8, got.get().size());
    }

    @Test
    void headerByIdMissIsEmptyNeverNull() {
        Optional<Map<String, Object>> got = store().headerById("nope");
        assertNotNull(got);
        assertTrue(got.isEmpty());
    }

    @Test
    void coverageShape() {
        var got = store().coverage("MATH");
        assertEquals(Set.of("Unit 1", "Unit 2"), got.keySet());
        assertEquals(2, got.get("Unit 1").get("total"));
        assertEquals(List.of("CONCEPT_MCQ", "NUMERIC"), got.get("Unit 1").get("formats"));
        assertEquals(List.of("core-scope", "legacy-scope"), got.get("Unit 1").get("scopes"));
        assertEquals(1, got.get("Unit 2").get("total"));
        assertEquals(List.of("NUMERIC"), got.get("Unit 2").get("formats"));
        assertEquals(List.of("core-scope"), got.get("Unit 2").get("scopes"));
    }

    @Test
    void coverageUnknownSubjectIsEmpty() {
        assertTrue(store().coverage("NOPE").isEmpty());
    }

    @Test
    void volumeCounts() {
        assertEquals(Map.of("MATH", 3, "PHYS", 1), store().volume());
    }

    @Test
    void recordsOfSingleObject() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var got = BankStore.recordsOf(mapper.readTree("{\"id\":\"x\"}"), mapper);
        assertEquals(1, got.size());
        assertEquals("x", got.get(0).get("id"));
    }

    @Test
    void recordsOfBatch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var got = BankStore.recordsOf(
                mapper.readTree("{\"questions\":[{\"id\":\"x\"},{\"id\":\"y\"}]}"), mapper);
        assertEquals(2, got.size());
        assertEquals("y", got.get(1).get("id"));
    }

    @Test
    void recordsOfNonArrayQuestionsIsSingle() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var got = BankStore.recordsOf(mapper.readTree("{\"questions\":\"x\"}"), mapper);
        assertEquals(1, got.size());
    }

    @Test
    void loadsClasspathBank() throws Exception {
        BankStore loaded = new BankStore();
        // Bank content grows independently; assert shape, not counts.
        assertTrue(loaded.volume().get("COMPUTING_FOUNDATIONS") >= 1);
        assertTrue(loaded.volume().get("JAVA_PROGRAMMING") >= 1);
        assertEquals(loaded.volume().get("COMPUTING_FOUNDATIONS"),
                loaded.search("COMPUTING_FOUNDATIONS", null, "everything", null).size());
        assertTrue(loaded.headerById("cf-2.1-0001").isPresent());
        assertEquals(8, loaded.headerById("cf-2.1-0001").get().size());
        assertTrue(loaded.headerById("nope").isEmpty());
    }
}
