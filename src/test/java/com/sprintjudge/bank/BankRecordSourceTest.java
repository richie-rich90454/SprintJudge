package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class BankRecordSourceTest {

	private static Resource json(String body) {
		return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8));
	}

	private static BankRecordSource source() {
		String valid = "{\"questions\":[\n"
				+ "  {\"id\":\"q1\",\"subject\":\"JAVA_PROGRAMMING\",\"unit\":\"Unit 1\",\"format\":\"CONCEPT_MCQ\",\"stem\":\"one\",\"answer\":{\"correctId\":\"A\"},\"options\":[{\"id\":\"A\",\"text\":\"x\"}]},\n"
				+ "  {\"id\":\"q2\",\"subject\":\"JAVA_PROGRAMMING\",\"unit\":\"Unit 2\",\"format\":\"NUMERIC\",\"stem\":\"two\",\"answer\":{\"value\":2}},\n"
				+ "  {\"id\":\"q3\",\"subject\":\"BIOLOGY\",\"unit\":\"Unit 1\",\"format\":\"CONCEPT_MCQ\",\"stem\":\"three\"},\n"
				+ "  {\"id\":\"q4\",\"format\":\"CONCEPT_MCQ\"}\n" + "]}\n";
		return new BankRecordSource(new Resource[]{json(valid), json("{not json")}, new ObjectMapper());
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
	void singleObjectDocumentIsLoaded() {
		String single = "{\"id\":\"solo\",\"subject\":\"JAVA_PROGRAMMING\",\"unit\":\"Unit 1\","
				+ "\"format\":\"CONCEPT_MCQ\"}";
		BankRecordSource source = new BankRecordSource(new Resource[]{json(single)}, new ObjectMapper());
		assertEquals(1, source.records("JAVA_PROGRAMMING", null).size());
	}

	@Test
	void nonArrayQuestionsFieldIsTreatedAsASingleRecord() {
		String single = "{\"questions\":\"not-an-array\",\"id\":\"solo2\","
				+ "\"subject\":\"JAVA_PROGRAMMING\",\"unit\":\"Unit 1\"}";
		BankRecordSource source = new BankRecordSource(new Resource[]{json(single)}, new ObjectMapper());
		assertEquals(1, source.records("JAVA_PROGRAMMING", null).size());
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

	@Test
	void indexIsBuiltInOnePassAcrossEverySubjectAndUnit() {
		String body = "{\"questions\":[{\"subject\":\"A\",\"unit\":\"U1\",\"topic\":\"Loops\"},"
				+ "{\"subject\":\"B\",\"unit\":\"U2\",\"topic\":\"Arrays\"}]}";
		int[] opens = {0};
		Resource counted = new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public InputStream getInputStream() throws java.io.IOException {
				opens[0]++;
				return super.getInputStream();
			}
		};
		BankRecordSource source = new BankRecordSource(new Resource[]{counted}, new ObjectMapper());
		for (int i = 0; i < 940; i++) {
			String unit = "U" + (i % 12);
			source.unitsOf("A");
			source.topicMatches("B", unit, "Loops");
		}
		assertEquals(Set.of("A", "B"), source.subjects());
		// 1,880 lookups used to re-read every file; the lazy index reads once.
		assertEquals(1, opens[0]);
	}

	@Test
	void unitsOfKeepsSortedDistinctNonBlankUnitsAndBlankFallback() {
		BankRecordSource source = source();
		assertEquals(List.of("Unit 1", "Unit 2"), source.unitsOf("JAVA_PROGRAMMING"));
		assertEquals(List.of(""), source.unitsOf("MATH"));
	}

	@Test
	void topicMatchesKeepsCaseInsensitiveTwoWayContainmentAndBlankUnitAggregation() {
		String body = "{\"questions\":[" + "{\"subject\":\"A\",\"unit\":\"U1\",\"topic\":\"Loop\"},"
				+ "{\"subject\":\"A\",\"unit\":\"U1\",\"topic\":\"Loops\"}," + "{\"subject\":\"A\",\"unit\":\"U1\"},"
				+ "{\"subject\":\"A\",\"unit\":\"U2\",\"topic\":\"Loops\"},"
				+ "{\"subject\":\"A\",\"topic\":\"Loops\"}]}";
		BankRecordSource source = new BankRecordSource(new Resource[]{json(body)}, new ObjectMapper());
		assertEquals(List.of("U1", "U2"), source.unitsOf("A"));
		assertEquals(2, source.topicMatches("A", "U1", "LOOPS"));
		assertEquals(1, source.topicMatches("A", "U2", "Loops"));
		assertEquals(4, source.topicMatches("A", "", "Loops"));
		assertEquals(4, source.topicMatches("A", null, "Loops"));
		assertEquals(0, source.topicMatches("A", "U1", ""));
		assertEquals(0, source.topicMatches("A", "U1", null));
		assertEquals(0, source.topicMatches("A", "U9", "Loops"));
		assertEquals(0, source.topicMatches("X", "U1", "Loops"));
	}

	@Test
	void indexSkipsBlankSubjects() {
		String body = "{\"questions\":[{\"subject\":\"   \",\"unit\":\"U\",\"topic\":\"T\"},"
				+ "{\"subject\":\"A\",\"unit\":\"U1\",\"topic\":\"Loops\"}," + "{\"subject\":\"B\",\"topic\":\"T\"}]}";
		BankRecordSource source = new BankRecordSource(new Resource[]{json(body)}, new ObjectMapper());
		assertEquals(Set.of("A", "B"), source.subjects());
		assertEquals(List.of(""), source.unitsOf("B"));
	}

	@Test
	void configMetaCachesParsedTopicAndUnit() {
		assertArrayEquals(new String[]{"", ""}, BankRecordSource.configMeta(null));
		assertArrayEquals(new String[]{"", ""}, BankRecordSource.configMeta("   "));
		String config = "{\"topic\":\"T\",\"unit\":\"U\"}";
		String[] meta = BankRecordSource.configMeta(config);
		assertArrayEquals(new String[]{"T", "U"}, meta);
		assertSame(meta, BankRecordSource.configMeta(config));
		assertArrayEquals(new String[]{"", ""}, BankRecordSource.configMeta("{oops"));
		assertArrayEquals(new String[]{"T", ""}, BankRecordSource.configMeta("{\"topic\":\"T\"}"));
		assertArrayEquals(new String[]{"", "U"}, BankRecordSource.configMeta("{\"unit\":\"U\"}"));
	}

	@Test
	void configMetaCacheEvictsOldestEntry() {
		String first = "{\"topic\":\"evict-0\"}";
		String[] meta = BankRecordSource.configMeta(first);
		assertSame(meta, BankRecordSource.configMeta(first));
		for (int i = 1; i <= 600; i++) {
			BankRecordSource.configMeta("{\"topic\":\"evict-" + i + "\"}");
		}
		assertArrayEquals(meta, BankRecordSource.configMeta(first));
	}
}
