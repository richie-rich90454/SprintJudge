package com.sprintjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Turns validated bank records into a normal playable quiz. The bank stays an
 * authoring surface; the generated quiz is a first-class room asset with the
 * same lifecycle as any teacher-created set.
 */
@Service
public class BankQuizBuilder {

	private static final Set<String> MCQ_FORMATS = Set.of("CONCEPT_MCQ", "STIMULUS_MCQ", "SOURCE_BASED",
			"ERROR_ANALYSIS", "DATA_INTERPRETATION", "CONCEPTUAL_REASONING", "RHETORICAL_ANALYSIS", "SYNTHESIS",
			"DIAGRAM", "CODE_TRACING", "BUG_SPOTTING", "CODE_COMPLETION", "MCQ");

	private final BankRecordSource records;
	private final ImportExportService importExportService;
	private final String codingBundle;
	private final Map<Integer, List<String>> csaUnits;
	private final Map<Integer, String> csaTitles;
	private final List<CsaPreset> csaPresets;
	private volatile ExportBundle.QuestionExport codingQuestion;

	public static final class CsaPreset {
		private int unit;
		private String title;
		private String description;
		private int questions;

		@SuppressWarnings("null")
		public CsaPreset() {
		}

		public CsaPreset(int unit, String title, String description, int questions) {
			this.unit = unit;
			this.title = title;
			this.description = description;
			this.questions = questions;
		}

		public int getUnit() {
			return unit;
		}
		public void setUnit(int unit) {
			this.unit = unit;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
		public int getQuestions() {
			return questions;
		}
		public void setQuestions(int questions) {
			this.questions = questions;
		}
	}

	static final class LoadedManifest {
		private Map<Integer, List<String>> units;
		private Map<Integer, String> titles;
		private List<CsaPreset> presets;

		@SuppressWarnings("null")
		public LoadedManifest() {
		}

		public LoadedManifest(Map<Integer, List<String>> units, Map<Integer, String> titles, List<CsaPreset> presets) {
			this.units = units;
			this.titles = titles;
			this.presets = presets;
		}

		public Map<Integer, List<String>> getUnits() {
			return units;
		}
		public void setUnits(Map<Integer, List<String>> units) {
			this.units = units;
		}
		public Map<Integer, String> getTitles() {
			return titles;
		}
		public void setTitles(Map<Integer, String> titles) {
			this.titles = titles;
		}
		public List<CsaPreset> getPresets() {
			return presets;
		}
		public void setPresets(List<CsaPreset> presets) {
			this.presets = presets;
		}
	}

	@Autowired
	public BankQuizBuilder(BankRecordSource records, ImportExportService importExportService) {
		this(records, importExportService, "seed/master-bundle.json");
	}

	BankQuizBuilder(BankRecordSource records, ImportExportService importExportService, String codingBundle) {
		this(records, importExportService, codingBundle, loadCsaManifest());
	}

	BankQuizBuilder(BankRecordSource records, ImportExportService importExportService, String codingBundle,
			LoadedManifest manifest) {
		this.records = records;
		this.importExportService = importExportService;
		this.codingBundle = codingBundle;
		this.csaUnits = Map.copyOf(manifest.getUnits());
		this.csaTitles = Map.copyOf(manifest.getTitles());
		this.csaPresets = List.copyOf(manifest.getPresets());
	}

	static LoadedManifest loadCsaManifest() {
		try (InputStream in = new ClassPathResource("seed/csa-featured.json").getInputStream()) {
			return loadCsaManifest(in);
		} catch (Exception e) {
			throw new IllegalStateException("csa featured manifest unavailable", e);
		}
	}

	static LoadedManifest loadCsaManifest(InputStream in) throws Exception {
		JsonNode root = new ObjectMapper().readTree(in);
		JsonNode presets = root.get("presets");
		if (presets == null || !presets.isArray()) {
			throw new IllegalArgumentException("presets missing");
		}
		Map<Integer, List<String>> units = new HashMap<>();
		Map<Integer, String> titles = new HashMap<>();
		List<CsaPreset> list = new ArrayList<>();
		for (JsonNode p : presets) {
			int unit = p.get("unit").asInt();
			String title = p.get("title").asText();
			String quizTitle = p.get("quizTitle").asText();
			String description = p.get("description").asText();
			int questions = p.get("questions").asInt();
			List<String> bankUnits = new ArrayList<>();
			for (JsonNode u : p.get("bankUnits"))
				bankUnits.add(u.asText());
			units.put(unit, List.copyOf(bankUnits));
			titles.put(unit, quizTitle);
			list.add(new CsaPreset(unit, title, description, questions));
		}
		return new LoadedManifest(units, titles, list);
	}

	public static final class BuildRequest {
		private String subject;
		private String unit;
		private int csaUnit;
		private int count;
		private String title;
		private long seed;
		private boolean includeCoding;

		@SuppressWarnings("null")
		public BuildRequest() {
		}

		public BuildRequest(String subject, String unit, int csaUnit, int count, String title, long seed,
				boolean includeCoding) {
			this.subject = subject;
			this.unit = unit;
			this.csaUnit = csaUnit;
			this.count = count;
			this.title = title;
			this.seed = seed;
			this.includeCoding = includeCoding;
		}

		public String getSubject() {
			return subject;
		}
		public void setSubject(String subject) {
			this.subject = subject;
		}
		public String getUnit() {
			return unit;
		}
		public void setUnit(String unit) {
			this.unit = unit;
		}
		public int getCsaUnit() {
			return csaUnit;
		}
		public void setCsaUnit(int csaUnit) {
			this.csaUnit = csaUnit;
		}
		public int getCount() {
			return count;
		}
		public void setCount(int count) {
			this.count = count;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public long getSeed() {
			return seed;
		}
		public void setSeed(long seed) {
			this.seed = seed;
		}
		public boolean isIncludeCoding() {
			return includeCoding;
		}
		public void setIncludeCoding(boolean includeCoding) {
			this.includeCoding = includeCoding;
		}
	}

	public static final class BuildResult {
		private String quizId;
		private int questions;
		private int skipped;

		@SuppressWarnings("null")
		public BuildResult() {
		}

		public BuildResult(String quizId, int questions, int skipped) {
			this.quizId = quizId;
			this.questions = questions;
			this.skipped = skipped;
		}

		public String getQuizId() {
			return quizId;
		}
		public void setQuizId(String quizId) {
			this.quizId = quizId;
		}
		public int getQuestions() {
			return questions;
		}
		public void setQuestions(int questions) {
			this.questions = questions;
		}
		public int getSkipped() {
			return skipped;
		}
		public void setSkipped(int skipped) {
			this.skipped = skipped;
		}
	}

	public List<CsaPreset> csaPresets() {
		return csaPresets;
	}

	public BuildResult build(BuildRequest request) {
		int count = Math.max(1, Math.min(30, request.getCount() <= 0 ? 12 : request.getCount()));
		boolean csa = request.getCsaUnit() >= 1 && request.getCsaUnit() <= 5;
		String subject = csa
				? "JAVA_PROGRAMMING"
				: blank(request.getSubject()) ? "JAVA_PROGRAMMING" : request.getSubject().trim();
		Set<String> playableFormats = union(MCQ_FORMATS, Set.of("MULTI_SELECT", "NUMERIC", "FORMULA_APPLICATION",
				"CALCULATION_WITH_UNITS", "NUMERIC_CALCULATION"));
		Random random = new Random(request.getSeed() == 0 ? 2026L + request.getCsaUnit() : request.getSeed());
		ExportBundle.QuestionExport coding = request.isIncludeCoding() ? codingQuestion(subject) : null;
		int bankTarget = coding == null ? count : Math.max(1, count - 1);

		List<Map<String, Object>> candidates = new ArrayList<>();
		if (request.getCsaUnit() == 5) {
			int perUnit = Math.max(1, (bankTarget + 3) / 4);
			for (int csaUnit = 1; csaUnit <= 4; csaUnit++) {
				List<Map<String, Object>> group = new ArrayList<>();
				for (String unit : csaUnits.get(csaUnit)) {
					group.addAll(records.playable(subject, unit, playableFormats));
				}
				Collections.shuffle(group, random);
				for (Map<String, Object> record : group.subList(0, Math.min(perUnit, group.size()))) {
					Map<String, Object> tagged = new LinkedHashMap<>(record);
					tagged.put("csaUnit", "CSA " + csaUnit);
					candidates.add(tagged);
				}
			}
		} else {
			List<String> units = request.getCsaUnit() >= 1 && request.getCsaUnit() <= 4
					? csaUnits.get(request.getCsaUnit())
					: blank(request.getUnit()) ? List.of("") : List.of(request.getUnit().trim());
			for (String unit : units) {
				List<Map<String, Object>> unitRecords = records.playable(subject, unit.isBlank() ? null : unit,
						playableFormats);
				Collections.shuffle(unitRecords, random);
				candidates.addAll(unitRecords);
			}
		}
		Collections.shuffle(candidates, random);
		int skipped = 0;

		List<ExportBundle.QuestionExport> questions = new ArrayList<>();
		for (Map<String, Object> record : candidates) {
			if (questions.size() >= bankTarget)
				break;
			ExportBundle.QuestionExport built = toQuestion(record, request.getCsaUnit());
			if (built == null) {
				skipped++;
				continue;
			}
			questions.add(built);
		}
		if (coding != null) {
			questions.add(new ExportBundle.QuestionExport(Ids.uuid(), coding.getType(), coding.getTitle(),
					coding.getDescription(), coding.getTimeLimitSec(), coding.getPointsBase(), coding.getConfig(),
					coding.getLanguagesAllowed()));
		}
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("No playable questions found for that bank selection");
		}

		String title = blank(request.getTitle())
				? csa ? csaTitles.get(request.getCsaUnit()) : "Java practice"
				: request.getTitle().trim();
		String quizId = Ids.uuid();
		ExportBundle bundle = new ExportBundle("1.0", Instant.now().getEpochSecond(),
				List.of(new ExportBundle.QuizExport(quizId, title, "Built from the question bank for classroom play.",
						false, questions)),
				Map.of());
		importExportService.importAll(Json.write(bundle), false);
		return new BuildResult(quizId, questions.size(), skipped);
	}

	@SuppressWarnings("unchecked")
	ExportBundle.QuestionExport toQuestion(Map<String, Object> record, int csaUnit) {
		String format = text(record, "format").toUpperCase(Locale.ROOT);
		String stem = formatStem(text(record, "stem"));
		String explanation = text(record, "explanation");
		if (stem.isBlank() || stem.length() > 4000 || explanation.isBlank())
			return null;
		String recordUnit = text(record, "csaUnit");
		Object raw = record.get("options");
		if (raw instanceof List<?>) {
			List<?> rawOptions = (List<?>) raw;
			if (!rawOptions.isEmpty()) {
				List<Map<String, Object>> options = (List<Map<String, Object>>) rawOptions;
				Map<String, Object> answer = asMap(record.get("answer"));
				if (MCQ_FORMATS.contains(format)) {
					int index = optionIndex(options, text(answer, "correctId"));
					if (index < 0)
						return null;
					Map<String, Object> config = new LinkedHashMap<>();
					List<String> optionTexts = new ArrayList<>();
					for (Map<String, Object> o : options) {
						optionTexts.add(text(o, "text"));
					}
					config.put("options", optionTexts);
					config.put("correctIndex", index);
					config.put("explanation", explanation);
					config.put("hint", text(record, "hint"));
					config.put("optionMeta", optionMeta(options));
					config.put("topic", text(record, "topic"));
					config.put("unit", text(record, "unit"));
					if (!recordUnit.isBlank())
						config.put("csaUnit", recordUnit);
					else if (csaUnit >= 1 && csaUnit <= 4)
						config.put("csaUnit", "CSA " + csaUnit);
					return new ExportBundle.QuestionExport(Ids.uuid(), "MCQ", titleFor(record), stem, 60,
							pointsFor(format), config, null);
				}
				if ("MULTI_SELECT".equals(format)) {
					List<Integer> indices = correctIndices(options, answer.get("correctIds"));
					if (indices.size() < 2)
						return null;
					Map<String, Object> config = new LinkedHashMap<>();
					List<String> optionTexts = new ArrayList<>();
					for (Map<String, Object> o : options) {
						optionTexts.add(text(o, "text"));
					}
					config.put("options", optionTexts);
					config.put("correctIndices", indices);
					config.put("explanation", explanation);
					config.put("hint", text(record, "hint"));
					config.put("optionMeta", optionMeta(options));
					config.put("topic", text(record, "topic"));
					config.put("unit", text(record, "unit"));
					if (!recordUnit.isBlank())
						config.put("csaUnit", recordUnit);
					else if (csaUnit >= 1 && csaUnit <= 4)
						config.put("csaUnit", "CSA " + csaUnit);
					return new ExportBundle.QuestionExport(Ids.uuid(), "MULTIPLE_SELECT", titleFor(record), stem, 90,
							200, config, null);
				}
			}
		}
		if (Set.of("NUMERIC", "FORMULA_APPLICATION", "CALCULATION_WITH_UNITS", "NUMERIC_CALCULATION")
				.contains(format)) {
			Map<String, Object> answer = asMap(record.get("answer"));
			double value = number(answer.get("value"), number(answer.get("answer"), Double.NaN));
			if (Double.isNaN(value))
				return null;
			Map<String, Object> config = new LinkedHashMap<>();
			config.put("answer", value);
			config.put("tolerance", number(answer.get("tolerance"), 0.0));
			config.put("explanation", explanation);
			config.put("hint", text(record, "hint"));
			config.put("topic", text(record, "topic"));
			config.put("unit", text(record, "unit"));
			if (!recordUnit.isBlank())
				config.put("csaUnit", recordUnit);
			else if (csaUnit >= 1 && csaUnit <= 4)
				config.put("csaUnit", "CSA " + csaUnit);
			return new ExportBundle.QuestionExport(Ids.uuid(), "NUMERIC", titleFor(record), stem, 90, 200, config,
					null);
		}
		return null;
	}

	private synchronized ExportBundle.QuestionExport codingQuestion(String subject) {
		if (!"JAVA_PROGRAMMING".equals(subject))
			return null;
		if (codingQuestion != null)
			return codingQuestion;
		try {
			String json = new ClassPathResource(codingBundle)
					.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
			ExportBundle bundle = Json.read(json, ExportBundle.class);
			ExportBundle.QuestionExport fallback = null;
			for (ExportBundle.QuizExport quiz : bundle.getQuizzes()) {
				boolean preferred = isPreferred(quiz.getTitle());
				for (ExportBundle.QuestionExport question : quiz.getQuestions()) {
					if (!"OJ_FULL".equals(question.getType()))
						continue;
					if (preferred) {
						codingQuestion = question;
						return question;
					}
					if (fallback == null)
						fallback = question;
				}
			}
			codingQuestion = fallback;
			return fallback;
		} catch (Exception e) {
			return null;
		}
	}

	private static Set<String> union(Set<String> a, Set<String> b) {
		java.util.HashSet<String> out = new java.util.HashSet<>(a);
		out.addAll(b);
		return out;
	}

	static boolean isPreferred(String title) {
		return title != null && (title.contains("Arrays") || title.contains("Lists") || title.contains("Recursion"));
	}

	static int pointsFor(String format) {
		if ("CODE_TRACING".equals(format) || "BUG_SPOTTING".equals(format))
			return 150;
		if ("CODE_COMPLETION".equals(format))
			return 200;
		return 100;
	}

	/**
	 * Bank stems carry a prose line followed by raw code. Fenced code renders with
	 * line numbers on the teacher stage; raw code does not. Never wrap a
	 * single-line inline snippet, and never touch a stem already fenced.
	 */
	static String formatStem(String stem) {
		if (stem == null || stem.contains("```"))
			return stem == null ? "" : stem;
		int newline = stem.indexOf('\n');
		if (newline < 0)
			return stem;
		String prose = stem.substring(0, newline).trim();
		String rest = stem.substring(newline + 1).trim();
		boolean code = rest.contains("{") || rest.contains(";") || rest.contains("class ");
		if (!code)
			return stem;
		return prose + "\n\n```java\n" + rest + "\n```";
	}

	private static String titleFor(Map<String, Object> record) {
		String topic = text(record, "topic");
		return topic.isBlank() ? "Bank question" : topic;
	}

	private static List<Map<String, Object>> optionMeta(List<Map<String, Object>> options) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> option : options) {
			if (text(option, "misconception").isBlank())
				continue;
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put("id", text(option, "id"));
			meta.put("misconception", text(option, "misconception"));
			out.add(meta);
		}
		return out;
	}

	private static int optionIndex(List<Map<String, Object>> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (id.equals(text(options.get(i), "id")))
				return i;
		}
		return -1;
	}

	private static List<Integer> correctIndices(List<Map<String, Object>> options, Object ids) {
		List<Integer> out = new ArrayList<>();
		if (ids instanceof List<?>) {
			List<?> list = (List<?>) ids;
			for (Object id : list) {
				int index = optionIndex(options, String.valueOf(id));
				if (index >= 0)
					out.add(index);
			}
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) value;
			return (Map<String, Object>) map;
		}
		return Map.of();
	}

	private static double number(Object value, double fallback) {
		if (value instanceof Number) {
			Number n = (Number) value;
			return n.doubleValue();
		}
		return fallback;
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : String.valueOf(value);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
