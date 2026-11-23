package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.bank.BankStore;
import com.sprintjudge.domain.analytics.AdaptiveSetRequest;
import com.sprintjudge.domain.analytics.AdaptiveSetResult;
import com.sprintjudge.domain.analytics.ClassReport;
import com.sprintjudge.domain.analytics.ItemReport;
import com.sprintjudge.domain.analytics.StudentReport;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.repository.Tables;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * Submission-derived reports joined with authored bank statistics, plus an
 * adaptive set builder that reuses the bank quiz conversion and import path.
 * All rates count each stored submission, so retries count as extra attempts.
 */
@Service
public class AnalyticsService {

	private static final String[] BANDS = {"0.00-0.25", "0.25-0.50", "0.50-0.75", "0.75-1.00"};

	private final DSLContext dsl;
	private final BankStore bankStore;
	private final BankRecordSource bankRecords;
	private final BankQuizBuilder builder;
	private final ImportExportService importExportService;

	public AnalyticsService(DSLContext dsl, BankStore bankStore, BankRecordSource bankRecords,
			BankQuizBuilder builder, ImportExportService importExportService) {
		this.dsl = dsl;
		this.bankStore = bankStore;
		this.bankRecords = bankRecords;
		this.builder = builder;
		this.importExportService = importExportService;
	}

	/**
	 * One report per player with submissions, optionally narrowed to one session.
	 *
	 * @param sessionId
	 *            session id filter, blank means all
	 * @return player reports, highest accuracy first, never null
	 */
	public List<StudentReport> studentReports(String sessionId) {
		Map<String, Tally> byPlayer = new HashMap<>();
		Map<String, String> names = new HashMap<>();
		for (Row row : loadRows(sessionId)) {
			Tally tally = byPlayer.computeIfAbsent(row.playerUuid, key -> new Tally());
			names.putIfAbsent(row.playerUuid, row.playerName);
			tally.add(row);
		}
		List<StudentReport> out = new ArrayList<>();
		for (Map.Entry<String, Tally> entry : byPlayer.entrySet()) {
			Tally t = entry.getValue();
			out.add(new StudentReport(entry.getKey(), names.get(entry.getKey()), t.sessions.size(), t.attempts,
					t.correct, rate(t.correct, t.attempts), t.score / (double) Math.max(1, t.attempts),
					t.attemptsSum / (double) Math.max(1, t.questions.size())));
		}
		out.sort(Comparator.comparingDouble(StudentReport::getAccuracy).reversed()
				.thenComparingInt(StudentReport::getAttempts).reversed()
				.thenComparing(StudentReport::getPlayerName, Comparator.nullsLast(String::compareTo)));
		return List.copyOf(out);
	}

	/**
	 * Class rollup plus a row per session, optionally narrowed to one session.
	 *
	 * @param sessionId
	 *            session id filter, blank means all
	 * @return class report, never null
	 */
	public ClassReport classReport(String sessionId) {
		Tally all = new Tally();
		Map<String, Tally> bySession = new HashMap<>();
		Map<String, Tally> byQuestion = new HashMap<>();
		for (Row row : loadRows(sessionId)) {
			all.add(row);
			bySession.computeIfAbsent(row.sessionId, key -> new Tally()).add(row);
			byQuestion.computeIfAbsent(row.questionId, key -> new Tally()).add(row);
		}
		Map<String, String> hosts = new HashMap<>();
		for (org.jooq.Record record : dsl.select(Tables.SESS_ID, Tables.SESS_HOST).from(Tables.GAME_SESSIONS).fetch()) {
			hosts.put(record.get(Tables.SESS_ID), record.get(Tables.SESS_HOST));
		}
		List<ClassReport.SessionStat> stats = new ArrayList<>();
		for (Map.Entry<String, Tally> entry : bySession.entrySet()) {
			Tally t = entry.getValue();
			stats.add(new ClassReport.SessionStat(entry.getKey(), hosts.get(entry.getKey()), t.players.size(),
					t.questions.size(), t.attempts, rate(t.correct, t.attempts),
					t.score / (double) Math.max(1, t.attempts)));
		}
		stats.sort(Comparator.comparing(ClassReport.SessionStat::getSessionId,
				Comparator.nullsLast(String::compareTo)));

		String hardest = null;
		String easiest = null;
		double lowest = 2.0;
		double highest = -1.0;
		for (Map.Entry<String, Tally> entry : byQuestion.entrySet()) {
			double value = rate(entry.getValue().correct, entry.getValue().attempts);
			if (value < lowest) {
				lowest = value;
				hardest = entry.getKey();
			}
			if (value > highest) {
				highest = value;
				easiest = entry.getKey();
			}
		}
		return new ClassReport(stats.size(), all.players.size(), all.questions.size(), all.attempts, all.correct,
				rate(all.correct, all.attempts), all.score / (double) Math.max(1, all.attempts), hardest, easiest,
				List.copyOf(stats));
	}

	/**
	 * Per-item report for one subject: observed rates joined to bank difficulty,
	 * discrimination, and response-time baseline. Only attempted items appear.
	 *
	 * @param subject
	 *            bank subject code
	 * @return item reports, most attempted first, never null
	 */
	public List<ItemReport> itemReports(String subject) {
		Map<String, Map<String, Object>> bankById = new LinkedHashMap<>();
		Map<String, Map<String, Object>> bankByKey = new HashMap<>();
		Map<String, Map<String, Object>> headers = new LinkedHashMap<>();
		for (Map<String, Object> header : bankStore.search(subject, null, null, null)) {
			headers.put(text(header, "id"), header);
		}
		for (Map<String, Object> record : bankRecords.records(subject, null)) {
			bankById.put(text(record, "id"), record);
			bankByKey.put(text(record, "unit") + "|" + text(record, "topic"), record);
		}
		Map<String, Tally> byQuestion = new HashMap<>();
		Map<String, List<Boolean>> flags = new HashMap<>();
		for (Row row : loadRows(null)) {
			byQuestion.computeIfAbsent(row.questionId, key -> new Tally()).add(row);
			flags.computeIfAbsent(row.questionId, key -> new ArrayList<>()).add(Boolean.valueOf(row.correct));
		}
		Map<String, String[]> meta = questionMeta();
		Map<String, Tally> byBankItem = new HashMap<>();
		Map<String, List<Boolean>> itemFlags = new HashMap<>();
		Map<String, String> questionIdByBankItem = new HashMap<>();
		for (Map.Entry<String, Tally> entry : byQuestion.entrySet()) {
			Map<String, Object> record = bankById.get(entry.getKey());
			if (record == null) {
				String[] tags = meta.get(entry.getKey());
				if (tags != null) {
					record = bankByKey.get(tags[1] + "|" + tags[0]);
				}
			}
			if (record == null) {
				continue;
			}
			String bankId = text(record, "id");
			Tally merged = byBankItem.computeIfAbsent(bankId, key -> new Tally());
			merged.absorb(entry.getValue());
			questionIdByBankItem.putIfAbsent(bankId, entry.getKey());
			itemFlags.computeIfAbsent(bankId, key -> new ArrayList<>()).addAll(flags.get(entry.getKey()));
		}
		List<ItemReport> out = new ArrayList<>();
		for (Map.Entry<String, Tally> entry : byBankItem.entrySet()) {
			Map<String, Object> record = bankById.get(entry.getKey());
			Map<String, Object> header = headers.get(entry.getKey());
			Tally t = entry.getValue();
			Map<String, Double> timing = timings(record.get("responseTimeBaseline"));
			Double authoredDifficulty = header == null
					? numberOrNull(record.get("difficulty"))
					: numberOrNull(header.get("difficulty"));
			out.add(new ItemReport(questionIdByBankItem.get(entry.getKey()), entry.getKey(), text(record, "topic"),
					text(record, "unit"), text(record, "topic"), t.attempts, t.correct, rate(t.correct, t.attempts),
					discrimination(itemFlags.get(entry.getKey())),
					t.attemptsSum / (double) Math.max(1, t.attempts),
					authoredDifficulty, numberOrNull(record.get("discrimination")),
					timing.get("p25Sec"), timing.get("medianSec"), timing.get("p75Sec")));
		}
		out.sort(Comparator.comparingInt(ItemReport::getAttempts).reversed().thenComparing(ItemReport::getBankId,
				Comparator.nullsLast(String::compareTo)));
		return List.copyOf(out);
	}

	/**
	 * Selects questions by difficulty band and discrimination, converts them with
	 * the bank quiz builder, and imports the result as a normal playable quiz.
	 *
	 * @param request
	 *            selection inputs, never null
	 * @return generated quiz id and selection summary
	 */
	public AdaptiveSetResult buildSet(AdaptiveSetRequest request) {
		if (request == null || request.getSubject() == null || request.getSubject().isBlank()) {
			throw new IllegalArgumentException("subject is required");
		}
		int size = request.getSize() <= 0 ? 10 : Math.min(30, request.getSize());
		String unit = request.getUnit() == null || request.getUnit().isBlank() ? null : request.getUnit().trim();
		List<Map<String, Object>> records = bankRecords.records(request.getSubject().trim(), unit);
		if (records.isEmpty()) {
			throw new IllegalArgumentException("No bank questions found for that selection");
		}
		boolean adaptive = "adaptive".equalsIgnoreCase(request.getMode());
		double target = request.getTargetDifficulty() > 0.0
				? clamp(request.getTargetDifficulty())
				: 0.5;
		Random random = new Random(request.getSeed() == 0L ? 2026L : request.getSeed());
		List<Candidate> scored = new ArrayList<>();
		for (Map<String, Object> record : records) {
			double difficulty = clamp(number(record.get("difficulty"), 0.5));
			double discrimination = number(record.get("discrimination"), 0.0);
			double score = adaptive
					? discrimination - 2.0 * Math.abs(difficulty - target)
					: discrimination;
			scored.add(new Candidate(record, score + random.nextDouble() * 0.01, band(difficulty)));
		}
		scored.sort(Comparator.comparingDouble(Candidate::score).reversed());
		List<Map<String, Object>> chosen = select(scored, size, adaptive);

		List<ExportBundle.QuestionExport> questions = new ArrayList<>();
		List<String> selectedIds = new ArrayList<>();
		Map<String, Integer> bands = new LinkedHashMap<>();
		double difficultySum = 0.0;
		double discriminationSum = 0.0;
		int skipped = 0;
		for (Map<String, Object> record : chosen) {
			if (questions.size() >= size) {
				break;
			}
			ExportBundle.QuestionExport question = builder.toQuestion(record, 0);
			if (question == null) {
				skipped++;
				continue;
			}
			difficultySum += number(record.get("difficulty"), 0.5);
			discriminationSum += number(record.get("discrimination"), 0.0);
			String band = BANDS[band(number(record.get("difficulty"), 0.5))];
			bands.merge(band, Integer.valueOf(1), Integer::sum);
			selectedIds.add(text(record, "id"));
			questions.add(question);
		}
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("No playable questions found for that bank selection");
		}
		String quizId = Ids.uuid();
		String title = request.getTitle() == null || request.getTitle().isBlank()
				? adaptive ? "Adaptive practice" : "Balanced practice"
				: request.getTitle().trim();
		ExportBundle bundle = new ExportBundle("1.0", Instant.now().getEpochSecond(),
				List.of(new ExportBundle.QuizExport(quizId, title, "Built from the question bank for classroom play.",
						false, questions)),
				Map.of());
		importExportService.importAll(Json.write(bundle), false);
		assert selectedIds.size() == questions.size();
		assert bands.values().stream().mapToInt(Integer::intValue).sum() == questions.size();
		return new AdaptiveSetResult(quizId, questions.size(), skipped, difficultySum / questions.size(),
				discriminationSum / questions.size(), List.copyOf(selectedIds), bands);
	}

	/** Balanced mode fills each difficulty band evenly; adaptive takes the top scores. */
	private static List<Map<String, Object>> select(List<Candidate> scored, int size, boolean adaptive) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (adaptive) {
			for (Candidate candidate : scored) {
				if (out.size() >= size * 2) {
					break;
				}
				out.add(candidate.record());
			}
			return out;
		}
		int[] used = new int[BANDS.length];
		for (Candidate candidate : scored) {
			int band = candidate.band();
			int quota = size / BANDS.length + (band < size % BANDS.length ? 1 : 0);
			if (used[band] < quota) {
				used[band]++;
				out.add(candidate.record());
			}
		}
		for (Candidate candidate : scored) {
			if (out.size() >= size * 2) {
				break;
			}
			if (!out.contains(candidate.record())) {
				out.add(candidate.record());
			}
		}
		return out;
	}

	private Map<String, String[]> questionMeta() {
		Map<String, String[]> meta = new HashMap<>();
		for (org.jooq.Record record : dsl.select(Tables.QUESTIONS_ID, Tables.QUESTIONS_CONFIG)
				.from(Tables.QUESTIONS).fetch()) {
			String topic = "";
			String unit = "";
			String config = record.get(Tables.QUESTIONS_CONFIG);
			if (config != null && !config.isBlank()) {
				try {
					JsonNode node = Json.readTree(config);
					topic = node.path("topic").asText("");
					unit = node.path("unit").asText("");
				} catch (RuntimeException ignored) {
					// A question with an unreadable config simply has no bank match.
				}
			}
			meta.put(record.get(Tables.QUESTIONS_ID), new String[] {topic, unit});
		}
		return meta;
	}

	private List<Row> loadRows(String sessionId) {
		Condition where = DSL.trueCondition();
		if (sessionId != null && !sessionId.isBlank()) {
			where = where.and(Tables.SUB_SESS.eq(sessionId.trim()));
		}
		return dsl.select(Tables.SUB_SESS, Tables.SUB_QUESTION, Tables.SUB_PUUID, Tables.SUB_PNAME,
				Tables.SUB_CORRECT, Tables.SUB_SCORE, Tables.SUB_ATTEMPTS)
				.from(Tables.SUBMISSIONS).where(where)
				.orderBy(Tables.SUB_QUESTION.asc(), Tables.SUB_AT.asc())
				.fetch(record -> new Row(record.get(Tables.SUB_SESS), record.get(Tables.SUB_QUESTION),
						record.get(Tables.SUB_PUUID), record.get(Tables.SUB_PNAME),
						Boolean.TRUE.equals(record.get(Tables.SUB_CORRECT)),
						record.get(Tables.SUB_SCORE) == null ? 0 : record.get(Tables.SUB_SCORE).intValue(),
						record.get(Tables.SUB_ATTEMPTS) == null ? 0 : record.get(Tables.SUB_ATTEMPTS).intValue()));
	}

	private static double discrimination(List<Boolean> flags) {
		if (flags == null || flags.size() < 2) {
			return 0.0;
		}
		int half = flags.size() / 2;
		int early = 0;
		int late = 0;
		for (int i = 0; i < flags.size(); i++) {
			if (Boolean.TRUE.equals(flags.get(i))) {
				if (i < half) {
					early++;
				} else {
					late++;
				}
			}
		}
		return Math.max(-1.0, Math.min(1.0, (double) early / half - (double) late / (flags.size() - half)));
	}

	private static Map<String, Double> timings(Object value) {
		Map<String, Double> out = new HashMap<>();
		if (value instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) value;
			for (String key : new String[] {"p25Sec", "medianSec", "p75Sec"}) {
				Object raw = map.get(key);
				if (raw instanceof Number) {
					out.put(key, Double.valueOf(((Number) raw).doubleValue()));
				}
			}
		}
		return out;
	}

	private static Double numberOrNull(Object value) {
		return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : null;
	}

	private static double number(Object value, double fallback) {
		return value instanceof Number ? ((Number) value).doubleValue() : fallback;
	}

	private static double rate(int correct, int attempts) {
		return attempts == 0 ? 0.0 : (double) correct / attempts;
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	private static int band(double difficulty) {
		return Math.min(BANDS.length - 1, Math.max(0, (int) (difficulty * BANDS.length)));
	}

	private static String text(Map<String, Object> record, String key) {
		Object value = record.get(key);
		return value == null ? "" : String.valueOf(value);
	}

	private static final class Row {
		private final String sessionId;
		private final String questionId;
		private final String playerUuid;
		private final String playerName;
		private final boolean correct;
		private final int score;
		private final int attempts;

		private Row(String sessionId, String questionId, String playerUuid, String playerName, boolean correct,
				int score, int attempts) {
			this.sessionId = sessionId;
			this.questionId = questionId;
			this.playerUuid = playerUuid;
			this.playerName = playerName;
			this.correct = correct;
			this.score = score;
			this.attempts = attempts;
		}
	}

	private static final class Tally {
		private int attempts;
		private int correct;
		private int score;
		private int attemptsSum;
		private final Set<String> sessions = new java.util.HashSet<>();
		private final Set<String> players = new java.util.HashSet<>();
		private final Set<String> questions = new java.util.HashSet<>();

		private void add(Row row) {
			attempts++;
			if (row.correct) {
				correct++;
			}
			score += row.score;
			attemptsSum += row.attempts;
			sessions.add(row.sessionId);
			players.add(row.playerUuid);
			questions.add(row.questionId);
		}

		private void absorb(Tally other) {
			attempts += other.attempts;
			correct += other.correct;
			score += other.score;
			attemptsSum += other.attemptsSum;
			sessions.addAll(other.sessions);
			players.addAll(other.players);
			questions.addAll(other.questions);
		}
	}

	private static final class Candidate {
		private final Map<String, Object> record;
		private final double score;
		private final int band;

		private Candidate(Map<String, Object> record, double score, int band) {
			this.record = record;
			this.score = score;
			this.band = band;
		}

		private Map<String, Object> record() {
			return record;
		}

		private double score() {
			return score;
		}

		private int band() {
			return band;
		}
	}
}
