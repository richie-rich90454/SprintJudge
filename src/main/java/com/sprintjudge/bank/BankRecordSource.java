package com.sprintjudge.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintjudge.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Full bank records for the quiz builder. Headers stay in {@link BankStore};
 * this source reads the selected subject/unit on demand and caches only that
 * slice, so a 200MB library never becomes a 200MB heap residency.
 *
 * A separate lightweight subject -> unit -> topic index is built in one pass on
 * first use. Recommendation logic reads that index instead of re-parsing the
 * bank for every subject/unit pair.
 */
@Component
public class BankRecordSource {

	private static final Logger log = LoggerFactory.getLogger(BankRecordSource.class);
	private static final String BANK_GLOB = "classpath*:bank/**/*.json";

	private static final int MAX_CACHED_SLICES = 2;
	private static final int MAX_CACHED_META = 512;
	private static final String[] EMPTY_META = {"", ""};

	private static final Map<String, String[]> metaCache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
			return size() > MAX_CACHED_META;
		}
	};

	private final Resource[] resources;
	private final ObjectMapper mapper;
	// A quiz build needs one subject/unit at a time. An unbounded cache kept full
	// records for every subject and exhausted the heap after a few builds.
	private final Map<String, List<Map<String, Object>>> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> eldest) {
			return size() > MAX_CACHED_SLICES;
		}
	};
	private volatile Set<String> subjectsCache;
	private volatile Map<String, Map<String, List<String>>> index;

	public BankRecordSource() throws IOException {
		this(new PathMatchingResourcePatternResolver().getResources(BANK_GLOB), new ObjectMapper());
	}

	public BankRecordSource(Resource[] resources, ObjectMapper mapper) {
		this.resources = resources;
		this.mapper = mapper;
	}

	/**
	 * Full records for a subject, optionally narrowed to a unit. Unreadable files
	 * are skipped with a log line; strict validation lives in CI.
	 */
	public synchronized List<Map<String, Object>> records(String subject, String unit) {
		String key = subject + "|" + (unit == null ? "" : unit);
		List<Map<String, Object>> hit = cache.get(key);
		if (hit != null) {
			return hit;
		}
		List<Map<String, Object>> loaded = load(subject, unit);
		cache.put(key, loaded);
		return loaded;
	}

	private List<Map<String, Object>> load(String subject, String unit) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Resource resource : resources) {
			try (InputStream in = resource.getInputStream()) {
				for (Map<String, Object> record : fullRecords(mapper.readTree(in))) {
					if (!subject.equals(text(record, "subject")))
						continue;
					if (unit != null && !unit.equals(text(record, "unit")))
						continue;
					out.add(record);
				}
			} catch (IOException | RuntimeException e) {
				log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
			}
		}
		return List.copyOf(out);
	}

	/**
	 * Distinct subject names present in the bank, backed by the one-pass index.
	 */
	public Set<String> subjects() {
		Set<String> cached = subjectsCache;
		if (cached != null)
			return cached;
		Set<String> copy = Set.copyOf(index().keySet());
		subjectsCache = copy;
		return copy;
	}

	/** Distinct non-blank units for a subject, sorted; blank unit if none exist. */
	public List<String> unitsOf(String subject) {
		Map<String, List<String>> units = index().get(subject);
		if (units == null)
			return List.of("");
		List<String> out = new ArrayList<>();
		for (String unit : new TreeSet<>(units.keySet())) {
			if (!unit.isBlank())
				out.add(unit);
		}
		return out.isEmpty() ? List.of("") : List.copyOf(out);
	}

	/** Topic matches for a subject/unit, including reversed substring matches. */
	public int topicMatches(String subject, String unit, String topic) {
		if (topic == null || topic.isBlank())
			return 0;
		Map<String, List<String>> units = index().get(subject);
		if (units == null)
			return 0;
		int count = 0;
		if (unit == null || unit.isBlank()) {
			for (List<String> topics : units.values()) {
				count += countMatches(topics, topic);
			}
		} else {
			List<String> topics = units.get(unit);
			if (topics != null)
				count = countMatches(topics, topic);
		}
		return count;
	}

	private static int countMatches(List<String> topics, String topic) {
		int count = 0;
		for (String candidate : topics) {
			if (containsIgnoreCase(candidate, topic) || containsIgnoreCase(topic, candidate))
				count++;
		}
		return count;
	}

	private static boolean containsIgnoreCase(String haystack, String needle) {
		if (needle.isBlank())
			return false;
		return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}

	/**
	 * Parsed topic/unit for an authored question config. Configs are immutable
	 * strings, so a bounded cache removes repeat parsing without going stale.
	 */
	public static synchronized String[] configMeta(String config) {
		if (config == null || config.isBlank())
			return EMPTY_META;
		String[] hit = metaCache.get(config);
		if (hit != null)
			return hit;
		String topic = "";
		String unit = "";
		try {
			JsonNode node = Json.readTree(config);
			topic = node.path("topic").asText("");
			unit = node.path("unit").asText("");
		} catch (RuntimeException ignored) {
			// An unreadable config simply contributes no bank metadata.
		}
		String[] meta = {topic, unit};
		metaCache.put(config, meta);
		return meta;
	}

	private synchronized Map<String, Map<String, List<String>>> index() {
		Map<String, Map<String, List<String>>> found = index;
		if (found != null)
			return found;
		Map<String, Map<String, List<String>>> built = new TreeMap<>();
		for (Resource resource : resources) {
			try (InputStream in = resource.getInputStream()) {
				for (Map<String, Object> record : fullRecords(mapper.readTree(in))) {
					String subject = text(record, "subject");
					if (subject.isBlank())
						continue;
					built.computeIfAbsent(subject, key -> new LinkedHashMap<>())
							.computeIfAbsent(text(record, "unit"), key -> new ArrayList<>()).add(text(record, "topic"));
				}
			} catch (IOException | RuntimeException e) {
				log.error("Skipping unreadable bank resource: {}", resource.getFilename(), e);
			}
		}
		found = built;
		index = found;
		return found;
	}

	/** Playable records for a unit, filtered to the mapped question formats. */
	public List<Map<String, Object>> playable(String subject, String unit, Set<String> formats) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> record : records(subject, unit)) {
			String format = text(record, "format").toUpperCase(Locale.ROOT);
			if (formats.contains(format))
				out.add(record);
		}
		return out;
	}

	/**
	 * Full records for the builder. Kept here rather than in BankStore so the
	 * header store never materialises answers or explanations.
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> fullRecords(JsonNode node) {
		if (node.has("questions") && node.path("questions").isArray()) {
			List<Map<String, Object>> batch = new ArrayList<>();
			for (JsonNode entry : node.path("questions")) {
				batch.add(mapper.convertValue(entry, Map.class));
			}
			return batch;
		}
		return List.of(mapper.convertValue(node, Map.class));
	}

	private static String text(Map<String, Object> record, String key) {
		Object value = record.get(key);
		return value == null ? "" : String.valueOf(value);
	}
}
