package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankRecordSourceTest {

    private static Resource json(String body) {
        return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8));
    }

    private static BankRecordSource source() {
        String valid = """
                {"questions":[
                  {"id":"q1","subject":"JAVA_PROGRAMMING","unit":"Unit 1","format":"CONCEPT_MCQ","stem":"one","answer":{"correctId":"A"},"options":[{"id":"A","text":"x"}]},
                  {"id":"q2","subject":"JAVA_PROGRAMMING","unit":"Unit 2","format":"NUMERIC","stem":"two","answer":{"value":2}},
                  {"id":"q3","subject":"BIOLOGY","unit":"Unit 1","format":"CONCEPT_MCQ","stem":"three"},
                  {"id":"q4","format":"CONCEPT_MCQ"}
                ]}
                """;
        return new BankRecordSource(
                new Resource[]{json(valid), json("{not json")}, new ObjectMapper());
    }

    @Test
    void recordsFilterBySubjectAndUnitAndSkipMalformedFiles() {
        BankRecordSource source = source();
        assertEquals(1, source.records("JAVA_PROGRAMMING", "Unit 1").size());
        assertEquals(2, source.records("JAVA_PROGRAMMING", null).size());
        assertEquals(1, source.records("BIOLOGY", "Unit 1").size());
        assertTrue(source.records("MATH", null).isEmpty());
    }

    @Test
    void playableFiltersFormatsAndCachesTheSlice() {
        BankRecordSource source = source();
        assertEquals(1, source.playable("JAVA_PROGRAMMING", "Unit 1", Set.of("CONCEPT_MCQ")).size());
        assertTrue(source.playable("JAVA_PROGRAMMING", "Unit 1", Set.of("NUMERIC")).isEmpty());
        assertEquals(2, source.records("JAVA_PROGRAMMING", null).size());
    }

    @Test
    void missingKeysDoNotThrow() {
        BankRecordSource source = source();
        List<?> all = source.records("JAVA_PROGRAMMING", null);
        assertEquals(2, all.size());
    }

    @Test
    void subjectsCollectsDistinctSubjectsAndCaches() {
        BankRecordSource source = source();
        Set<String> first = source.subjects();
        assertEquals(Set.of("JAVA_PROGRAMMING", "BIOLOGY"), first);
        // second call exercises the cache-hit branch
        assertEquals(first, source.subjects());
    }
}