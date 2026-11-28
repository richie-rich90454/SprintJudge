package com.sprintjudge.bank;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Classpath question bank, loaded once at construction. Only answer-free
 * headers are retained: id, subject, unit, topic, scope, format, difficulty,
 * stem. Options, answers, explanations, and every other field are dropped at
 * load, so the bank never holds a full question in memory. Correct answers
 * never leave this store.
 *
 * Headers are kept in columnar arrays with repeated subject/unit/scope/format
 * values interned. A map per header would cost roughly an object plus eight
 * entries and eight value strings per question; at 169000 questions that alone
 * dominated the heap. Columnar storage also makes the search scan sequential.
 */
@Component
public class BankStore {

	private static final Logger log = LoggerFactory.getLogger(BankStore.class);
	private static final String[] HEADER_KEYS = {"id", "subject", "unit", "topic", "scope", "format", "stem"};

	private final String[] ids;
	private final String[] subjects;
	private final String[] units;
	private final String[] topics;
	private final String[] scopes;
	private final String[] formats;
	private final String[] stems;
	private final Object[] difficulties;
	private final int skippedResources;
	private final Map<String, Integer> idIndex;
	private final Map<String, int[]> bySubject;

	/**
	 * Loads every bank JSON document from the classpath.
	 *
	 * @throws IOException
	 *             when a bank document cannot be read
	 */
	public BankStore() throws IOException {
		this(new PathMatchingResourcePatternResolver(), new ObjectMapper());
	}

	BankStore(PathMatchingResourcePatternResolver resolver, ObjectMapper mapper) throws IOException {
		this(loadAll(resources(resolver), mapper));
	}

	private BankStore(Loaded loaded) {
		this.ids = loaded.ids;
		this.subjects = loaded.subjects;
		this.units = loaded.units;
		this.topics = loaded.topics;
		this.scopes = loaded.scopes;
		this.formats = loaded.formats;
		this.stems = loaded.stems;
		this.difficulties = loaded.difficulties;
		this.skippedResources = loaded.skipped;
		this.idIndex = indexIds(ids);
		this.bySubject = indexSubjects(subjects);
	}

	BankStore(List<Map<String, Object>> seed) {
		Columns columns = new Columns();
		Map<String, String> pool = new HashMap<>();
		for (Map<String, Object> record : seed) {
			columns.add(record, pool);
		}
		this.ids = columns.ids();
		this.subjects = columns.subjects();
		this.units = columns.units();
		this.topics = columns.topics();
		this.scopes = columns.scopes();
		this.formats = columns.formats();
		this.stems = columns.stems();
		this.difficulties = columns.difficulties();
		this.skippedResources = 0;
		this.idIndex = indexIds(ids);
		this.bySubject = indexSubjects(subjects);
	}

	private static Resource[] resources(PathMatchingResourcePatternResolver resolver) throws IOException {
		return resolver.getResources("classpath*:bank/**/*.json");
	}

	/**
	 * Answer-free header for one index in the columnar store.
	 */
	private Map<String, Object> header(int i) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("id", ids[i]);
		out.put("subject", subjects[i]);
		out.put("unit", units[i]);
		out.put("topic", topics[i]);
		out.put("scope", scopes[i]);
		out.put("format", formats[i]);
		out.put("difficulty", difficulties[i]);
		out.put("stem", stems[i]);
		return out;
	}

	private static Map<String, Integer> indexIds(String[] ids) {
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < ids.length; i++) {
			out.putIfAbsent(ids[i], Integer.valueOf(i));
		}
		return out;
	}

	private static Map<String, int[]> indexSubjects(String[] subjects) {
		Map<String, List<Integer>> grouped = new HashMap<>();
		for (int i = 0; i < subjects.length; i++) {
			grouped.computeIfAbsent(subjects[i], key -> new ArrayList<>()).add(Integer.valueOf(i));
		}
		Map<String, int[]> out = new HashMap<>();
		for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
			int[] indices = new int[entry.getValue().size()];
			for (int i = 0; i < indices.length; i++) {
				indices[i] = entry.getValue().get(i).intValue();
			}
			out.put(entry.getKey(), indices);
		}
		return out;
	}

	/**
	 * Result of a bank load: the columnar headers and the skip count. The headers
	 * view exists for tests that load small fixtures; production never expands it.
	 */
	static final class Loaded {
		private final String[] ids;
		private final String[] subjects;
		private final String[] units;
		private final String[] topics;
		private final String[] scopes;
		private final String[] formats;
		private final String[] stems;
		private final Object[] difficulties;
		private final int skipped;

		Loaded() {
			this.ids = new String[0];
			this.subjects = new String[0];
			this.units = new String[0];
			this.topics = new String[0];
			this.scopes = new String[0];
			this.formats = new String[0];
			this.stems = new String[0];
			this.difficulties = new Object[0];
			this.skipped = 0;
		}

		Loaded(Columns columns, int skipped) {
			this.ids = columns.ids();
			this.subjects = columns.subjects();
			this.units = columns.units();
			this.topics = columns.topics();
			this.scopes = columns.scopes();
			this.formats = columns.formats();
			this.stems = columns.stems();
			this.difficulties = columns.difficulties();
			this.skipped = skipped;
		}

		List<Map<String, Object>> getHeaders() {
			List<Map<String, Object>> out = new ArrayList<>(ids.length);
			for (int i = 0; i < ids.length; i++) {
				Map<String, Object> header = new LinkedHashMap<>();
				header.put("id", ids[i]);
				header.put("subject", subjects[i]);
				header.put("unit", units[i]);
				header.put("topic", topics[i]);
				header.put("scope", scopes[i]);
				header.put("format", formats[i]);
				header.put("difficulty", difficulties[i]);
				header.put("stem", stems[i]);
				out.add(header);
			}
			return out;
		}

		int getSkipped() {
			return skipped;
		}
	}

	/**
	 * Column accumulator. Repeated scalar values are interned so 169000 records
	 * share a handful of subject/unit/scope/format string instances.
	 */
	private static final class Columns {
		private final List<String> ids = new ArrayList<>();
		private final List<String> subjects = new ArrayList<>();
		private final List<String> units = new ArrayList<>();
		private final List<String> topics = new ArrayList<>();
		private final List<String> scopes = new ArrayList<>();
		private final List<String> formats = new ArrayList<>();
		private final List<String> stems = new ArrayList<>();
		private final List<Object> difficulties = new ArrayList<>();

		void add(Map<String, Object> header, Map<String, String> pool) {
			ids.add(intern(pool, text(header, "id")));
			subjects.add(intern(pool, text(header, "subject")));
			units.add(intern(pool, text(header, "unit")));
			topics.add(intern(pool, text(header, "topic")));
			scopes.add(intern(pool, text(header, "scope")));
			formats.add(intern(pool, text(header, "format")));
			stems.add(text(header, "stem"));
			difficulties.add(header.get("difficulty"));
		}

		String[] ids() {
			return ids.toArray(new String[0]);
		}
		String[] subjects() {
			return subjects.toArray(new String[0]);
		}
		String[] units() {
			return units.toArray(new String[0]);
		}
		String[] topics() {
			return topics.toArray(new String[0]);
		}
		String[] scopes() {
			return scopes.toArray(new String[0]);
		}
		String[] formats() {
			return formats.toArray(new String[0]);
		}
		String[] stems() {
			return stems.toArray(new String[0]);
		}
		Object[] difficulties() {
			return difficulties.toArray();
		}
	}

	private static String intern(Map<String, String> pool, String value) {
		String found = pool.get(value);
		if (found != null) {
			return found;
		}
		pool.put(value, value);
		return value;
	}

	private static String text(Map<String, Object> question, String key) {
		return String.valueOf(question.get(key));
	}

	/**
	 * Loads every readable bank file. A malformed or half-written file must never
	 * take the whole application down: the strict validator reports it in CI, while
	 * runtime keeps serving the other 99.9% of the library. Only one file's headers
	 * are materialised at a time, so peak memory stays flat across the whole bank.
	 */
	static Loaded loadAll(Resource[] resources, ObjectMapper mapper) {
		Columns columns = new Columns();
		Map<String, String> pool = new HashMap<>();
		int skipped = 0;
		JsonFactory factory = mapper.getFactory();
		for (Resource resource : resources) {
			try (InputStream in = resource.getInputStream(); JsonParser parser = factory.createParser(in)) {
				Map<String, Object> root = new LinkedHashMap<>();
				for (String key : HEADER_KEYS) {
					root.put(key, "");
				}
				boolean batched = false;
				if (parser.nextToken() == JsonToken.START_OBJECT) {
					while (parser.nextToken() == JsonToken.FIELD_NAME) {
						String field = parser.currentName();
						JsonToken value = parser.nextToken();
						if ("questions".equals(field) && value == JsonToken.START_ARRAY) {
							while (true) {
								JsonToken entry = parser.nextToken();
								if (entry == JsonToken.END_ARRAY) {
									break;
								}
								columns.add(header(mapper.readTree(parser), mapper), pool);
							}
							batched = true;
						} else {
							root.put(field, parser.readValueAs(Object.class));
						}
					}
				}
				if (!batched) {
					columns.add(root, pool);
				}
			} catch (IOException | RuntimeException e) {
				skipped++;
				log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
			}
		}
		if (skipped > 0) {
			log.warn("Bank loaded with {} unreadable file(s); run `make check-bank` to see details", skipped);
		}
		return new Loaded(columns, skipped);
	}

	/**
	 * Extracts answer-free headers from one JSON document.
	 */
	static List<Map<String, Object>> recordsOf(JsonNode node, ObjectMapper mapper) {
		if (node.has("questions") && node.path("questions").isArray()) {
			List<Map<String, Object>> batch = new ArrayList<>();
			for (JsonNode entry : node.path("questions")) {
				batch.add(header(entry, mapper));
			}
			return batch;
		}
		return List.of(header(node, mapper));
	}

	private static Map<String, Object> header(JsonNode question, ObjectMapper mapper) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (String key : HEADER_KEYS) {
			out.put(key, question.path(key).asText());
		}
		JsonNode difficulty = question.get("difficulty");
		out.put("difficulty", difficulty == null ? null : mapper.convertValue(difficulty, Object.class));
		return out;
	}

	/** Number of bank files skipped because they were unreadable at startup. */
	public int skippedResources() {
		return skippedResources;
	}

	/**
	 * Answer-free headers matching the supplied filters.
	 */
	public List<Map<String, Object>> search(String subject, String unit, String scope, String format) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (int i : bySubject.getOrDefault(subject, new int[0])) {
			if (!matchScope(scopes[i], scope)) {
				continue;
			}
			if (!matchText(units[i], unit)) {
				continue;
			}
			if (!matchText(formats[i], format)) {
				continue;
			}
			out.add(header(i));
		}
		return List.copyOf(out);
	}

	/**
	 * Answer-free header for one question id.
	 */
	public Optional<Map<String, Object>> headerById(String id) {
		Integer index = idIndex.get(id);
		return index == null ? Optional.empty() : Optional.of(header(index.intValue()));
	}

	/**
	 * Per-unit format and scope coverage for one subject.
	 */
	public Map<String, Map<String, Object>> coverage(String subject) {
		Map<String, List<Integer>> byUnit = new TreeMap<>();
		for (int i : bySubject.getOrDefault(subject, new int[0])) {
			List<Integer> list = byUnit.get(units[i]);
			if (list == null) {
				list = new ArrayList<>();
				byUnit.put(units[i], list);
			}
			list.add(Integer.valueOf(i));
		}
		Map<String, Map<String, Object>> out = new LinkedHashMap<>();
		for (Map.Entry<String, List<Integer>> entry : byUnit.entrySet()) {
			Set<String> foundFormats = new TreeSet<>();
			Set<String> foundScopes = new TreeSet<>();
			for (Integer index : entry.getValue()) {
				foundFormats.add(formats[index.intValue()]);
				foundScopes.add(scopes[index.intValue()]);
			}
			Map<String, Object> unit = new LinkedHashMap<>();
			unit.put("total", Integer.valueOf(entry.getValue().size()));
			unit.put("formats", List.copyOf(foundFormats));
			unit.put("scopes", List.copyOf(foundScopes));
			out.put(entry.getKey(), unit);
		}
		return out;
	}

	/**
	 * Question totals per subject.
	 */
	public Map<String, Integer> volume() {
		Map<String, Integer> out = new TreeMap<>();
		for (Map.Entry<String, int[]> entry : bySubject.entrySet()) {
			out.put(entry.getKey(), Integer.valueOf(entry.getValue().length));
		}
		return out;
	}

	private static boolean matchScope(String value, String scope) {
		if (scope == null || scope.isBlank() || "everything".equals(scope)) {
			return true;
		}
		return scope.equals(value);
	}

	private static boolean matchText(String value, String filter) {
		if (filter == null || filter.isBlank()) {
			return true;
		}
		return filter.equals(value);
	}
}
