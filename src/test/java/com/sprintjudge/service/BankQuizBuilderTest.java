package com.sprintjudge.service;

import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.util.Json;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankQuizBuilderTest {

	private static Resource json(String body) {
		return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8));
	}

	private static String option(String id, String text) {
		return "{\"id\":\"" + id + "\",\"text\":\"" + text + "\",\"misconception\":\"specific wrong belief " + id
				+ "\"}";
	}

	private static String question(String id, String subject, String unit, String format, String answer, String options,
			String explanation) {
		return "{\"id\":\"" + id + "\",\"subject\":\"" + subject + "\",\"unit\":\"" + unit + "\",\"topic\":\"Topic "
				+ id + "\",\"format\":\"" + format + "\"" + ",\"stem\":\"What is " + id + "?\"" + ",\"answer\":"
				+ answer + ",\"options\":[" + options + "]" + ",\"explanation\":\"" + explanation + "\""
				+ ",\"hint\":\"hint\"}";
	}

	private static BankRecordSource source(String body) {
		return new BankRecordSource(new Resource[]{json(body)}, new com.fasterxml.jackson.databind.ObjectMapper());
	}

	private static String javaRecords() {
		return "{\"questions\":["
				+ question("q1", "JAVA_PROGRAMMING", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"B\"}",
						option("A", "no") + "," + option("B", "yes") + "," + option("C", "maybe") + ","
								+ option("D", "never"),
						"teaches why")
				+ ","
				+ question("q2", "JAVA_PROGRAMMING", "Unit 2", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "teaches why")
				+ ","
				+ question("q3", "JAVA_PROGRAMMING", "Unit 2", "NUMERIC", "{\"value\":7.5,\"tolerance\":0.01}", "",
						"teaches why")
				+ ","
				+ question("q4", "JAVA_PROGRAMMING", "Unit 1", "MULTI_SELECT", "{\"correctIds\":[\"A\",\"C\"]}",
						option("A", "yes") + "," + option("B", "no") + "," + option("C", "yes"), "teaches why")
				+ ","
				+ question("q5", "JAVA_PROGRAMMING", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "")
				+ "," + question("q6", "JAVA_PROGRAMMING", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"Z\"}",
						option("A", "yes") + "," + option("B", "no"), "why")
				+ "]}";
	}

	@Test
	void buildsAPlayableCsaQuizAndMapsFormats() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer);

		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 1, 5, null, 2, false));

		assertEquals(4, result.getQuestions());
		assertEquals(2, result.getSkipped());
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(importer).importAll(json.capture(), eq(false));
		ExportBundle bundle = Json.read(json.getValue(), ExportBundle.class);
		List<ExportBundle.QuestionExport> questions = bundle.getQuizzes().get(0).getQuestions();
		Set<String> types = new java.util.HashSet<String>();
		for (ExportBundle.QuestionExport q : questions) {
			types.add(q.getType());
		}
		assertTrue(types.contains("MCQ"));
		assertTrue(types.contains("MULTIPLE_SELECT"));
		assertTrue(types.contains("NUMERIC"));
		{
			boolean streamAllResult = true;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport q : questions) {
				if (!(q.getConfig().containsKey("explanation"))) {
					streamAllResult = false;
					break;
				}
			}
			assertTrue(streamAllResult);
		}
		{
			boolean streamAllResult = true;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport q : questions) {
				if (!("CSA 1".equals(q.getConfig().get("csaUnit")))) {
					streamAllResult = false;
					break;
				}
			}
			assertTrue(streamAllResult);
		}
	}

	@Test
	void mixedCsaPullsOnePerUnitAndTagsTheSourceUnit() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		String body = "{\"questions\":["
				+ question("m1", "JAVA_PROGRAMMING", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "why")
				+ ","
				+ question("m2", "JAVA_PROGRAMMING", "Unit 3", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "why")
				+ ","
				+ question("m3", "JAVA_PROGRAMMING", "Unit 5", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "why")
				+ "," + question("m4", "JAVA_PROGRAMMING", "Unit 6", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
						option("A", "yes") + "," + option("B", "no"), "why")
				+ "]}";
		BankQuizBuilder builder = new BankQuizBuilder(source(body), importer);
		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest(null, null, 5, 4, null, 3, false));
		assertEquals(4, result.getQuestions());
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(importer).importAll(json.capture(), eq(false));
		ExportBundle bundle = Json.read(json.getValue(), ExportBundle.class);
		Set<Object> units = new java.util.HashSet<java.lang.Object>();
		for (com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport q : bundle.getQuizzes().get(0)
				.getQuestions()) {
			units.add(q.getConfig().get("csaUnit"));
		}
		assertEquals(Set.of("CSA 1", "CSA 2", "CSA 3", "CSA 4"), units);
	}

	@Test
	void codingQuestionIsAppendedFromTheConfiguredBundle() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer, "seed/coding-bundle.json");
		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 0, 3, null, 4, true));
		assertEquals(3, result.getQuestions());
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(importer).importAll(json.capture(), eq(false));
		ExportBundle bundle = Json.read(json.getValue(), ExportBundle.class);
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport q : bundle.getQuizzes().get(0)
					.getQuestions()) {
				if ("OJ_FULL".equals(q.getType())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void nonPreferredCodingQuestionIsUsedAsFallback() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer, "seed/coding-fallback.json");
		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 0, 2, null, 8, true));
		assertEquals(2, result.getQuestions());
		builder.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 0, 2, null, 8, true));
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(importer, times(2)).importAll(json.capture(), eq(false));
		ExportBundle bundle = Json.read(json.getValue(), ExportBundle.class);
		{
			boolean streamMatchResult = false;
			for (com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport q : bundle.getQuizzes().get(0)
					.getQuestions()) {
				if ("OJ_FULL".equals(q.getType())) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void codingIsSkippedForNonJavaSubjects() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		String body = "{\"questions\":[" + question("p1", "PYTHON", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
				option("A", "yes") + "," + option("B", "no"), "why") + "]}";
		BankQuizBuilder builder = new BankQuizBuilder(source(body), importer, "seed/coding-bundle.json");
		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest("PYTHON", null, 0, 1, null, 5, true));
		assertEquals(1, result.getQuestions());
	}

	@Test
	void missingCodingBundleIsIgnored() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer, "seed/does-not-exist.json");
		BankQuizBuilder.BuildResult result = builder
				.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", "Unit 1", 0, 1, null, 6, true));
		assertEquals(1, result.getQuestions());
	}

	@Test
	void toQuestionCoversEveryShapeAndFallbackBranch() {
		ImportExportService importer = mock(ImportExportService.class);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer);

		java.util.Map<String, Object> base = new java.util.HashMap<>();
		base.put("format", "CONCEPT_MCQ");
		base.put("stem", "Stem");
		base.put("explanation", "Why");
		base.put("topic", "Topic");
		base.put("unit", "Unit 1");
		base.put("options", List.of(java.util.Map.of("id", "A", "text", "yes", "misconception", "wrong A"),
				java.util.Map.of("id", "B", "text", "no", "misconception", "")));
		base.put("answer", java.util.Map.of("correctId", "A"));

		assertNull(builder.toQuestion(with(base, "stem", null), 0));
		assertNull(builder.toQuestion(with(base, "stem", "x".repeat(4001)), 0));
		assertNull(builder.toQuestion(with(base, "explanation", ""), 0));
		assertNull(builder.toQuestion(with(base, "options", "not a list", "format", "CONCEPT_MCQ"), 0));
		assertNull(builder.toQuestion(with(base, "answer", java.util.Map.of("correctId", "Z")), 0));
		assertNotNull(builder.toQuestion(with(base, "csaUnit", ""), 1));
		assertNotNull(builder.toQuestion(with(base, "csaUnit", "CSA 2"), 0));
		assertNotNull(builder.toQuestion(with(base, "topic", "", "csaUnit", ""), 0));
		assertNotNull(builder.toQuestion(with(base, "topic", null), 0));

		java.util.Map<String, Object> multi = new java.util.HashMap<>(base);
		multi.put("format", "MULTI_SELECT");
		multi.put("answer", java.util.Map.of("correctIds", List.of("A")));
		assertNull(builder.toQuestion(multi, 0));
		multi.put("answer", java.util.Map.of("correctIds", List.of("A", "Z")));
		assertNull(builder.toQuestion(multi, 1));
		multi.put("answer", java.util.Map.of("correctIds", List.of("A", "B")));
		assertNotNull(builder.toQuestion(multi, 1));
		assertNotNull(builder.toQuestion(with(multi, "csaUnit", "CSA 3"), 0));
		assertNotNull(builder.toQuestion(with(multi, "csaUnit", ""), 1));
		multi.remove("answer");
		assertNull(builder.toQuestion(multi, 0));

		java.util.Map<String, Object> numeric = new java.util.HashMap<>();
		numeric.put("format", "NUMERIC");
		numeric.put("stem", "Stem");
		numeric.put("explanation", "Why");
		numeric.put("answer", java.util.Map.of("value", 1.5));
		assertNotNull(builder.toQuestion(numeric, 1));
		numeric.put("csaUnit", "");
		assertNotNull(builder.toQuestion(numeric, 1));
		numeric.put("csaUnit", "CSA 4");
		assertNotNull(builder.toQuestion(numeric, 0));
		numeric.put("answer", java.util.Map.of("answer", 2));
		assertNotNull(builder.toQuestion(numeric, 0));
		numeric.put("answer", java.util.Map.of("value", "nope"));
		assertNull(builder.toQuestion(numeric, 0));
		numeric.remove("answer");
		assertNull(builder.toQuestion(numeric, 0));

		assertNull(builder.toQuestion(with(base, "format", "EXPLANATION"), 0));
		assertNull(builder.toQuestion(with(base, "options", List.of(), "format", "EXPLANATION"), 0));
	}

	private static java.util.Map<String, Object> with(java.util.Map<String, Object> base, String key, Object value) {
		java.util.Map<String, Object> copy = new java.util.HashMap<>(base);
		copy.put(key, value);
		return copy;
	}

	private static java.util.Map<String, Object> with(java.util.Map<String, Object> base, String key, Object value,
			String key2, Object value2) {
		java.util.Map<String, Object> copy = with(base, key, value);
		copy.put(key2, value2);
		return copy;
	}

	@Test
	void buildRequestDefaultsAndOverridesAreAllExercised() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder javaBuilder = new BankQuizBuilder(source(javaRecords()), importer);
		javaBuilder.build(new BankQuizBuilder.BuildRequest(null, null, 0, 0, null, 0, false));
		javaBuilder.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", "", 0, 2, "Custom", 1, false));
		javaBuilder.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 6, 99, null, 2, false));
		String python = "{\"questions\":[" + question("p1", "PYTHON", "Unit 1", "CONCEPT_MCQ", "{\"correctId\":\"A\"}",
				option("A", "yes") + "," + option("B", "no"), "why") + "]}";
		BankQuizBuilder pythonBuilder = new BankQuizBuilder(source(python), importer);
		pythonBuilder.build(new BankQuizBuilder.BuildRequest("PYTHON", null, 0, 1, null, 3, true));
	}

	@Test
	void unsupportedShapesAreRejectedInsteadOfGuessed() {
		ImportExportService importer = mock(ImportExportService.class);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer);
		assertNull(builder.toQuestion(Map.of("format", "EXPLANATION", "stem", "Prose", "explanation", "Why"), 0));
	}

	@Test
	void emptySelectionIsRejected() {
		ImportExportService importer = mock(ImportExportService.class);
		BankQuizBuilder builder = new BankQuizBuilder(source("{\"questions\":[]}"), importer);
		assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				builder.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", "Unit 9", 0, 2, null, 1, false));
			}
		});
	}

	@Test
	void formatStemWrapsCodeBlocksAndLeavesInlineOrFencedTextAlone() {
		assertTrue(BankQuizBuilder.formatStem("What value?\nint x = 1;\nSystem.out.print(x);").contains("```java"));
		assertEquals("One line int x = 1;", BankQuizBuilder.formatStem("One line int x = 1;"));
		assertEquals("```java\nint x = 1;\n```", BankQuizBuilder.formatStem("```java\nint x = 1;\n```"));
		assertEquals("Prose\nMore prose", BankQuizBuilder.formatStem("Prose\nMore prose"));
		assertEquals("", BankQuizBuilder.formatStem(null));
		assertTrue(BankQuizBuilder.formatStem("Prose\nclass X {").contains("```java"));
		assertTrue(BankQuizBuilder.formatStem("Prose\nint x = 1;").contains("```java"));
		assertTrue(BankQuizBuilder.formatStem("Prose\nclass X").contains("```java"));
	}

	@Test
	void preferredCodingTitlesAndScoringAreExplicit() {
		assertTrue(BankQuizBuilder.isPreferred("Java Arrays"));
		assertTrue(BankQuizBuilder.isPreferred("Java Lists"));
		assertTrue(BankQuizBuilder.isPreferred("Java Recursion"));
		assertEquals(false, BankQuizBuilder.isPreferred("Java Coding"));
		assertEquals(false, BankQuizBuilder.isPreferred(null));
		assertEquals(150, BankQuizBuilder.pointsFor("CODE_TRACING"));
		assertEquals(150, BankQuizBuilder.pointsFor("BUG_SPOTTING"));
		assertEquals(200, BankQuizBuilder.pointsFor("CODE_COMPLETION"));
		assertEquals(100, BankQuizBuilder.pointsFor("CONCEPT_MCQ"));
	}

	@Test
	void numericAndMultiSelectConfigsAreShapedForTheEvaluator() {
		ImportExportService importer = mock(ImportExportService.class);
		when(importer.importAll(anyString(), eq(false))).thenReturn(1);
		BankQuizBuilder builder = new BankQuizBuilder(source(javaRecords()), importer);
		builder.build(new BankQuizBuilder.BuildRequest("JAVA_PROGRAMMING", null, 0, 5, "T", 9, false));
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(importer).importAll(json.capture(), eq(false));
		ExportBundle bundle = Json.read(json.getValue(), ExportBundle.class);
		List<Map<String, Object>> configs = new java.util.ArrayList<Map<String, Object>>();
		for (ExportBundle.QuestionExport q : bundle.getQuizzes().get(0).getQuestions()) {
			configs.add(q.getConfig());
		}
		{
			boolean streamMatchResult = false;
			for (java.util.Map<java.lang.String, java.lang.Object> c : configs) {
				if (c.containsKey("correctIndex")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
		{
			boolean streamMatchResult = false;
			for (java.util.Map<java.lang.String, java.lang.Object> c : configs) {
				if (c.containsKey("correctIndices")) {
					streamMatchResult = true;
					break;
				}
			}
			assertTrue(streamMatchResult);
		}
	}

	@Test
	void csaManifestLoadsFromResources() {
		BankQuizBuilder.LoadedManifest m = BankQuizBuilder.loadCsaManifest();
		org.junit.jupiter.api.Assertions.assertFalse(m.getPresets().isEmpty());
		org.junit.jupiter.api.Assertions.assertTrue(m.getUnits().containsKey(1));
	}

	@Test
	void malformedCsaManifestThrows() {
		assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() throws java.lang.Throwable {
				BankQuizBuilder.loadCsaManifest(new ByteArrayInputStream("{}".getBytes()));
			}
		});
	}
}
