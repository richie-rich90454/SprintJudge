package com.sprintjudge.service;

import com.sprintjudge.TestDb;
import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.bank.BankStore;
import com.sprintjudge.domain.analytics.AdaptiveSetRequest;
import com.sprintjudge.domain.analytics.AdaptiveSetResult;
import com.sprintjudge.domain.analytics.ClassReport;
import com.sprintjudge.domain.analytics.ItemReport;
import com.sprintjudge.domain.analytics.StudentReport;
import com.sprintjudge.domain.dto.export.ExportBundle;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AnalyticsServiceTest {

	private DSLContext dsl;
	private BankStore bankStore;
	private BankRecordSource bankRecords;
	private BankQuizBuilder builder;
	private ImportExportService importExport;
	private AnalyticsService service;

	@BeforeEach
	void setUp() throws Exception {
		dsl = TestDb.inMemory();
		bankStore = mock(BankStore.class);
		bankRecords = mock(BankRecordSource.class);
		builder = mock(BankQuizBuilder.class);
		importExport = mock(ImportExportService.class);
		service = new AnalyticsService(dsl, bankStore, bankRecords, builder, importExport);

		dsl.execute("INSERT INTO quizzes (id, title) VALUES ('q1','Quiz One')");
		dsl.execute("INSERT INTO game_sessions (id, quiz_id, pin_code, host_user_id) VALUES "
				+ "('s1','q1','111','host1'), ('s2','q1','222','host2')");
		dsl.execute("INSERT INTO questions (id, quiz_id, title, config) VALUES "
				+ "('questA','q1','A','{\"topic\":\"T2\",\"unit\":\"U2\"}'), "
				+ "('questB','q1','B','{\"topic\":\"TX\",\"unit\":\"UX\"}'), "
				+ "('questD','q1','D',NULL), ('questE','q1','E','  '), ('questF','q1','F','not-json')");
		dsl.execute("INSERT INTO submissions (id, game_session_id, question_id, player_uuid, player_name, is_correct, "
				+ "score_earned, attempt_count, submitted_at) VALUES "
				+ "('sub1','s1','questA','u1','Alice',1,10,1,100), "
				+ "('sub2','s1','questA','u1','Alice',0,NULL,1,110), "
				+ "('sub3','s1','questB','u2',NULL,1,5,NULL,120), " + "('sub4','s2','questC','u3','Carl',1,7,1,130), "
				+ "('sub5','s1','b1','u4','Dana',1,3,1,140), " + "('sub6','s1','b1','u4','Dana',0,2,1,150), "
				+ "('sub7','s1','b3','u5','Eve',1,1,1,160)");
	}

	@Test
	void studentReportsAggregateSortAndFilter() {
		List<StudentReport> all = service.studentReports(null);
		assertEquals(5, all.size());
		assertEquals(0.5, all.get(0).getAccuracy(), 1e-9);
		assertTrue(all.stream().anyMatch(r -> "u3".equals(r.getPlayerUuid())));
		assertTrue(all.stream().anyMatch(r -> "u5".equals(r.getPlayerUuid())));
		StudentReport alice = all.stream().filter(r -> "u1".equals(r.getPlayerUuid())).findFirst().orElseThrow();
		assertEquals("Alice", alice.getPlayerName());
		assertEquals(1, alice.getSessions());
		assertEquals(2, alice.getAttempts());
		assertEquals(1, alice.getCorrect());
		assertEquals(0.5, alice.getAccuracy(), 1e-9);
		assertEquals(5.0, alice.getAvgScore(), 1e-9);
		assertEquals(2.0, alice.getAvgAttemptsPerItem(), 1e-9);
		StudentReport nameless = all.stream().filter(r -> "u2".equals(r.getPlayerUuid())).findFirst().orElseThrow();
		assertNull(nameless.getPlayerName());

		assertEquals(5, service.studentReports(" ").size());
		assertEquals(4, service.studentReports(" s1 ").size());
	}

	@Test
	void classReportRollsUpSessionsAndQuestions() {
		ClassReport report = service.classReport(null);
		assertEquals(2, report.getSessionCount());
		assertEquals(5, report.getPlayers());
		assertEquals(5, report.getQuestions());
		assertEquals(7, report.getAttempts());
		assertNotNull(report.getHardestQuestionId());
		assertNotNull(report.getEasiestQuestionId());
		assertEquals(2, report.getSessionStats().size());
		ClassReport.SessionStat stat = report.getSessionStats().get(0);
		assertEquals("s1", stat.getSessionId());
		assertEquals("host1", stat.getHostUserId());
		assertEquals(4, stat.getPlayers());
		assertEquals(4, stat.getQuestions());
		assertEquals(1, service.classReport("s1").getSessionCount());
	}

	@Test
	void itemReportsJoinBankHeadersMetadataAndTiming() {
		when(bankStore.search("MATH", null, null, null)).thenReturn(List.of(Map.of("id", "b1", "difficulty", 0.4),
				Map.of("id", "b2", "difficulty", "unknown"), Map.of("id", "b4", "difficulty", 0.1)));
		when(bankRecords.records("MATH", null)).thenReturn(
				List.of(record("b1", "U1", "T1", 0.4, 0.5, Map.of("p25Sec", 10, "medianSec", 20, "p75Sec", 30)),
						record("b2", "U2", "T2", "hard", "high", "none"),
						record("b3", "UX", "TX", 0.7, 0.1, Map.of("p25Sec", "bad"))));

		List<ItemReport> reports = service.itemReports("MATH");
		assertEquals(3, reports.size());
		ItemReport b1 = reports.stream().filter(r -> "b1".equals(r.getBankId())).findFirst().orElseThrow();
		assertNotNull(b1.getQuestionId());
		assertEquals(2, b1.getAttempts());
		assertEquals(0.5, b1.getPValue(), 1e-9);
		assertEquals(0.4, b1.getBankDifficulty(), 1e-9);
		assertEquals(0.5, b1.getBankDiscrimination(), 1e-9);
		assertEquals(10.0, b1.getP25Sec(), 1e-9);
		assertEquals(20.0, b1.getMedianSec(), 1e-9);
		assertEquals(30.0, b1.getP75Sec(), 1e-9);
		ItemReport b2 = reports.stream().filter(r -> "b2".equals(r.getBankId())).findFirst().orElseThrow();
		assertNull(b2.getBankDifficulty());
		assertNull(b2.getBankDiscrimination());
		assertNull(b2.getP25Sec());
		ItemReport b3 = reports.stream().filter(r -> "b3".equals(r.getBankId())).findFirst().orElseThrow();
		assertEquals(0.7, b3.getBankDifficulty(), 1e-9);
		assertNull(b3.getP25Sec());
	}

	private static Map<String, Object> record(String id, String unit, String topic, Object difficulty,
			Object discrimination, Object timing) {
		Map<String, Object> record = new java.util.LinkedHashMap<>();
		record.put("id", id);
		record.put("unit", unit);
		record.put("topic", topic);
		record.put("difficulty", difficulty);
		record.put("discrimination", discrimination);
		record.put("responseTimeBaseline", timing);
		return record;
	}

	@Test
	void buildSetValidatesSelection() {
		assertThrows(IllegalArgumentException.class, () -> service.buildSet(null));
		AdaptiveSetRequest blank = new AdaptiveSetRequest();
		assertThrows(IllegalArgumentException.class, () -> service.buildSet(blank));
		blank.setSubject("  ");
		assertThrows(IllegalArgumentException.class, () -> service.buildSet(blank));
		AdaptiveSetRequest empty = request("MATH", 0, "balanced", 0.0, 0L);
		when(bankRecords.records("MATH", null)).thenReturn(List.of());
		assertThrows(IllegalArgumentException.class, () -> service.buildSet(empty));
	}

	@Test
	void buildSetBalancedSelectsImportsAndSummarizes() {
		when(bankRecords.records("MATH", null)).thenReturn(records());
		when(builder.toQuestion(any(), anyInt()))
				.thenAnswer(inv -> question(String.valueOf(inv.getArgument(0, Map.class).get("id"))));
		AdaptiveSetRequest request = request(" MATH ", 5, "balanced", 0.0, 0L);
		request.setUnit("  ");
		AdaptiveSetResult result = service.buildSet(request);
		assertEquals(5, result.getQuestionCount());
		assertEquals(0, result.getSkipped());
		assertEquals(5, result.getSelectedIds().size());
		assertEquals(5, result.getDifficultyBands().values().stream().mapToInt(Integer::intValue).sum());
		assertTrue(result.getAvgDifficulty() >= 0.0 && result.getAvgDifficulty() <= 1.0);
		verify(importExport).importAll(any(), eq(false));
	}

	@Test
	void buildSetTitleBranchesAndBalancedBreak() {
		when(bankRecords.records("MATH", null)).thenReturn(records());
		when(builder.toQuestion(any(), anyInt()))
				.thenAnswer(inv -> question(String.valueOf(inv.getArgument(0, Map.class).get("id"))));

		AdaptiveSetRequest blankTitle = request("MATH", 2, "balanced", 0.0, 7L);
		blankTitle.setTitle("  ");
		assertEquals(2, service.buildSet(blankTitle).getQuestionCount());

		AdaptiveSetRequest customTitle = request("MATH", 2, "balanced", 0.0, 7L);
		customTitle.setTitle("  My Quiz  ");
		service.buildSet(customTitle);
		org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(importExport, org.mockito.Mockito.times(2)).importAll(json.capture(), eq(false));
		assertTrue(json.getAllValues().get(1).contains("My Quiz"));
	}

	@Test
	void buildSetAdaptiveCompletesLoopWithoutBreak() {
		when(bankRecords.records("MATH", null)).thenReturn(records());
		when(builder.toQuestion(any(), anyInt()))
				.thenAnswer(inv -> question(String.valueOf(inv.getArgument(0, Map.class).get("id"))));
		AdaptiveSetRequest request = request("MATH", 4, "ADAPTIVE", 0.5, 3L);
		assertEquals(4, service.buildSet(request).getQuestionCount());
	}

	@Test
	void buildSetAdaptiveClampsAndPathsTitle() {
		when(bankRecords.records("MATH", "U1")).thenReturn(records());
		when(builder.toQuestion(any(), anyInt()))
				.thenAnswer(inv -> question(String.valueOf(inv.getArgument(0, Map.class).get("id"))));
		AdaptiveSetRequest request = request("MATH", 1, "ADAPTIVE", 1.5, 42L);
		request.setUnit(" U1 ");
		AdaptiveSetResult result = service.buildSet(request);
		assertEquals(1, result.getQuestionCount());
		assertEquals(1, result.getSelectedIds().size());
	}

	@Test
	void buildSetSkipsUnplayableAndUsesDefaultTitle() {
		when(bankRecords.records("MATH", null)).thenReturn(records());
		when(builder.toQuestion(any(), anyInt())).thenReturn(null);
		AdaptiveSetRequest request = request("MATH", 100, "other", -1.0, 0L);
		assertThrows(IllegalArgumentException.class, () -> service.buildSet(request));

		when(builder.toQuestion(any(), anyInt()))
				.thenAnswer(inv -> question(String.valueOf(inv.getArgument(0, Map.class).get("id"))));
		AdaptiveSetResult result = service.buildSet(request);
		assertEquals(8, result.getQuestionCount());
		verify(importExport, org.mockito.Mockito.atLeastOnce()).importAll(any(), anyBoolean());
	}

	private static AdaptiveSetRequest request(String subject, int size, String mode, double target, long seed) {
		AdaptiveSetRequest request = new AdaptiveSetRequest();
		request.setSubject(subject);
		request.setSize(size);
		request.setMode(mode);
		request.setTargetDifficulty(target);
		request.setSeed(seed);
		return request;
	}

	private static List<Map<String, Object>> records() {
		return List.of(record("r1", "U1", "T1", 0.1, 0.2, null), record("r2", "U1", "T1", 0.3, "high", null),
				record("r3", "U1", "T1", 0.6, 0.4, null), record("r4", "U1", "T1", 0.9, 0.1, null),
				record("r5", "U1", "T1", 1.5, -0.3, null), record("r6", "U1", "T1", -0.5, 0.8, null),
				record("r7", "U1", "T1", "bad", 0.6, null), record("r8", "U1", "T1", 0.5, 0.7, null));
	}

	private static ExportBundle.QuestionExport question(String id) {
		return new ExportBundle.QuestionExport(id, "MCQ", "Title", "Description", 30, 10, Map.of(), List.of());
	}

	@Test
	void privateHelpersCoverDefensiveBranches() throws Exception {
		assertEquals(0.0, call("rate", new Class<?>[]{int.class, int.class}, 1, 0));
		assertEquals(0.5, call("rate", new Class<?>[]{int.class, int.class}, 1, 2));
		assertEquals(0.0, call("clamp", new Class<?>[]{double.class}, -2.0));
		assertEquals(1.0, call("clamp", new Class<?>[]{double.class}, 2.0));
		assertEquals(0.5, call("clamp", new Class<?>[]{double.class}, 0.5));
		assertEquals(0, call("band", new Class<?>[]{double.class}, -0.1));
		assertEquals(3, call("band", new Class<?>[]{double.class}, 1.2));
		assertEquals(2, call("band", new Class<?>[]{double.class}, 0.5));
		assertEquals(0.5, call("number", new Class<?>[]{Object.class, double.class}, "bad", 0.5));
		assertEquals(0.3, call("number", new Class<?>[]{Object.class, double.class}, 0.3, 0.5));
		assertNull(call("numberOrNull", new Class<?>[]{Object.class}, "bad"));
		assertEquals(0.4, call("numberOrNull", new Class<?>[]{Object.class}, 0.4));
		assertEquals("", call("text", new Class<?>[]{Map.class, String.class}, Map.of(), "id"));
		assertEquals("x", call("text", new Class<?>[]{Map.class, String.class}, Map.of("id", "x"), "id"));
		assertEquals(0.0, call("discrimination", new Class<?>[]{List.class}, (Object) null));
		assertEquals(0.0, call("discrimination", new Class<?>[]{List.class}, List.of(true)));
		assertEquals(1.0, call("discrimination", new Class<?>[]{List.class}, List.of(true, true, false, false)));
		assertTrue(((Map<?, ?>) call("timings", new Class<?>[]{Object.class}, (Object) null)).isEmpty());
		assertTrue(((Map<?, ?>) call("timings", new Class<?>[]{Object.class}, Map.of("p25Sec", "x"))).isEmpty());
		Map<?, ?> timings = (Map<?, ?>) call("timings", new Class<?>[]{Object.class},
				Map.of("p25Sec", 1, "medianSec", 2.5, "p75Sec", 3));
		assertEquals(1.0, timings.get("p25Sec"));
		assertEquals(2.5, timings.get("medianSec"));
		assertEquals(3.0, timings.get("p75Sec"));
	}

	private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
		Method method = AnalyticsService.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method.invoke(null, args);
	}
}
