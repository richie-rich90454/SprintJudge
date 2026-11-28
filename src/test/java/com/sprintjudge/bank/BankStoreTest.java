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

@SuppressWarnings("all")
class BankStoreTest {

	private static Map<String, Object> question(String id, String subject, String unit, String topic, String scope,
			String format, double difficulty) {
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
		return new BankStore(List.of(question("a1", "MATH", "Unit 1", "Algebra", "core-scope", "CONCEPT_MCQ", 0.7),
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
		List<Map<String, Object>> got = store().search("MATH", "Unit 1", "core-scope", "CONCEPT_MCQ");
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
		List<Map<String, Object>> got = store().search("PHYS", null, null, null);
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
		Map<String, Map<String, Object>> got = store().coverage("MATH");
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
		List<Map<String, Object>> got = BankStore.recordsOf(mapper.readTree("{\"id\":\"x\"}"), mapper);
		assertEquals(1, got.size());
		assertEquals("x", got.get(0).get("id"));
	}

	@Test
	void recordsOfBatch() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<Map<String, Object>> got = BankStore
				.recordsOf(mapper.readTree("{\"questions\":[{\"id\":\"x\"},{\"id\":\"y\"}]}"), mapper);
		assertEquals(2, got.size());
		assertEquals("y", got.get(1).get("id"));
	}

	@Test
	void recordsOfNonArrayQuestionsIsSingle() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<Map<String, Object>> got = BankStore.recordsOf(mapper.readTree("{\"questions\":\"x\"}"), mapper);
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
		assertTrue(loaded.skippedResources() >= 0);
	}

	@Test
	void loadAllReportsNoSkipsForCleanInput() {
		org.springframework.core.io.ByteArrayResource clean = new org.springframework.core.io.ByteArrayResource(
				"{\"questions\":[{\"id\":\"x\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		BankStore.Loaded loaded = BankStore.loadAll(new org.springframework.core.io.Resource[]{clean},
				new ObjectMapper());
		assertEquals(0, loaded.getSkipped());
		assertEquals(1, loaded.getHeaders().size());
	}

	@Test
	void loadAllSkipsAnUnreadableResourceAndKeepsTheRest() {
		org.springframework.core.io.ByteArrayResource broken = new org.springframework.core.io.ByteArrayResource(
				new byte[0]) {
			@Override
			public java.io.InputStream getInputStream() throws java.io.IOException {
				throw new java.io.IOException("unreadable");
			}

			@Override
			public String getFilename() {
				return "broken.json";
			}
		};
		org.springframework.core.io.ByteArrayResource clean = new org.springframework.core.io.ByteArrayResource(
				"{\"questions\":[{\"id\":\"y\"}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		BankStore.Loaded loaded = BankStore.loadAll(new org.springframework.core.io.Resource[]{broken, clean},
				new ObjectMapper());
		assertEquals(1, loaded.getSkipped());
		assertEquals(1, loaded.getHeaders().size());
	}

	@Test
	void indexesArePopulatedForConstantTimeLookups() throws Exception {
		BankStore store = store();
		assertEquals(Map.of("a1", 0, "a2", 1, "a3", 2, "b1", 3), field(store, "idIndex"));
		Map<?, ?> bySubject = (Map<?, ?>) field(store, "bySubject");
		assertEquals(List.of(0, 1, 2), java.util.Arrays.stream((int[]) bySubject.get("MATH")).boxed().toList());
		assertEquals(List.of(3), java.util.Arrays.stream((int[]) bySubject.get("PHYS")).boxed().toList());
	}

	@Test
	void duplicateIdsKeepTheFirstHeaderWithoutDroppingQuestions() {
		List<Map<String, Object>> seed = List.of(question("a1", "MATH", "U1", "First", "core-scope", "F", 0.7),
				question("a1", "MATH", "U2", "Second", "core-scope", "F", 0.2));
		BankStore store = new BankStore(seed);
		assertEquals("First", store.headerById("a1").get().get("topic"));
		assertEquals(2, store.search("MATH", null, null, null).size());
	}

	@Test
	void emptyStoreHasEmptyIndexesAndLookups() throws Exception {
		BankStore store = new BankStore(List.of());
		assertTrue(store.headerById("x").isEmpty());
		assertTrue(store.volume().isEmpty());
		assertTrue(store.search("MATH", null, null, null).isEmpty());
		assertTrue(store.coverage("MATH").isEmpty());
		java.lang.reflect.Constructor<BankStore> ctor = BankStore.class.getDeclaredConstructor(BankStore.Loaded.class);
		ctor.setAccessible(true);
		assertEquals(0, ctor.newInstance(new BankStore.Loaded()).skippedResources());
	}

	@Test
	void streamingLoadMatchesTreeBasedExtraction() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		String batch = "{\"meta\":{\"k\":\"v\"},\"questions\":["
				+ "{\"id\":\"x\",\"subject\":\"MATH\",\"unit\":\"U1\",\"topic\":\"T\",\"scope\":\"s\",\"format\":\"F\",\"stem\":\"stem\",\"difficulty\":{\"bloom\":\"KNOW\"},\"answer\":{\"correctId\":\"A\"}},"
				+ "{\"id\":\"y\",\"difficulty\":2,\"subject\":\"MATH\"}," + "{\"id\":\"z\",\"difficulty\":null},"
				+ "{\"id\":\"w\",\"difficulty\":\"hard\"}," + "{\"difficulty\":true}," + "5" + "]}";
		assertEquals(BankStore.recordsOf(mapper.readTree(batch), mapper), loaded(batch, mapper).getHeaders());
		String single = "{\"id\":\"s\",\"subject\":\"A\",\"difficulty\":0.5}";
		assertEquals(BankStore.recordsOf(mapper.readTree(single), mapper), loaded(single, mapper).getHeaders());
		String nonArray = "{\"questions\":\"nope\",\"id\":\"n\",\"subject\":\"A\"}";
		assertEquals(BankStore.recordsOf(mapper.readTree(nonArray), mapper), loaded(nonArray, mapper).getHeaders());
		String arrayRoot = "[]";
		assertEquals(BankStore.recordsOf(mapper.readTree(arrayRoot), mapper), loaded(arrayRoot, mapper).getHeaders());
	}

	private static BankStore.Loaded loaded(String body, ObjectMapper mapper) {
		return BankStore.loadAll(new org.springframework.core.io.Resource[]{json(body)}, mapper);
	}

	private static org.springframework.core.io.Resource json(String body) {
		return new org.springframework.core.io.ByteArrayResource(
				body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static Object field(BankStore store, String name) throws Exception {
		java.lang.reflect.Field field = BankStore.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(store);
	}
}
