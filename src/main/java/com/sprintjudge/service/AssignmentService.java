package com.sprintjudge.service;

import com.sprintjudge.domain.assignment.Assignment;
import com.sprintjudge.domain.assignment.GradeResult;
import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.repository.Tables;
import com.sprintjudge.util.Ids;
import com.sprintjudge.util.Json;
import com.sprintjudge.util.RepoUtil;
import jakarta.annotation.PostConstruct;
import org.eclipse.jdt.annotation.Nullable;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Persistence and rules for async assignments, rosters, progress and grades. */
@Service
public class AssignmentService {

	private static final int MAX_CSV_BYTES = 1_000_000;
	private static final int MAX_ROSTER_ROWS = 5_000;

	private static final Table<?> ASSIGNMENTS = DSL.table("assignments");
	private static final Field<String> A_ID = DSL.field("id", SQLDataType.VARCHAR);
	private static final Field<String> A_QUIZ_ID = DSL.field("quiz_id", SQLDataType.VARCHAR);
	private static final Field<String> A_TITLE = DSL.field("title", SQLDataType.VARCHAR);
	private static final Field<String> A_DESC = DSL.field("description", SQLDataType.VARCHAR);
	private static final Field<Long> A_RELEASE = DSL.field("release_at", SQLDataType.BIGINT);
	private static final Field<Long> A_DUE = DSL.field("due_at", SQLDataType.BIGINT);
	private static final Field<String> A_CREATED_BY = DSL.field("created_by", SQLDataType.VARCHAR);
	private static final Field<Long> A_CREATED_AT = DSL.field("created_at", SQLDataType.BIGINT);
	private static final Field<String> A_LTI_DEPLOYMENT = DSL.field("lti_deployment_id", SQLDataType.VARCHAR);

	private static final Table<?> ROSTER = DSL.table("assignment_roster");
	private static final Field<String> R_ID = DSL.field("id", SQLDataType.VARCHAR);
	private static final Field<String> R_ASSIGNMENT = DSL.field("assignment_id", SQLDataType.VARCHAR);
	private static final Field<String> R_STUDENT = DSL.field("student_id", SQLDataType.VARCHAR);
	private static final Field<String> R_NAME = DSL.field("name", SQLDataType.VARCHAR);
	private static final Field<String> R_EMAIL = DSL.field("email", SQLDataType.VARCHAR);

	private static final Table<?> PROGRESS = DSL.table("assignment_progress");
	private static final Field<String> P_ID = DSL.field("id", SQLDataType.VARCHAR);
	private static final Field<String> P_ASSIGNMENT = DSL.field("assignment_id", SQLDataType.VARCHAR);
	private static final Field<String> P_STUDENT = DSL.field("student_id", SQLDataType.VARCHAR);
	private static final Field<String> P_STATUS = DSL.field("status", SQLDataType.VARCHAR);
	private static final Field<Long> P_STARTED = DSL.field("started_at", SQLDataType.BIGINT);
	private static final Field<Long> P_SUBMITTED = DSL.field("submitted_at", SQLDataType.BIGINT);
	private static final Field<Integer> P_SCORE = DSL.field("score", SQLDataType.INTEGER);
	private static final Field<Integer> P_MAX_SCORE = DSL.field("max_score", SQLDataType.INTEGER);
	private static final Field<Long> P_GRADED = DSL.field("graded_at", SQLDataType.BIGINT);

	private final DSLContext dsl;
	private final DataSource dataSource;

	public AssignmentService(DSLContext dsl, DataSource dataSource) {
		this.dsl = dsl;
		this.dataSource = dataSource;
	}

	/**
	 * The bundled DDL is idempotent, so running it here is safe even after the
	 * migration runner picks the file up. Keeps this vertical slice self-contained
	 * without touching the shared migration bootstrap.
	 */
	@PostConstruct
	void initSchema() {
		try (Connection conn = dataSource.getConnection()) {
			EncodedResource resource = new EncodedResource(
					new ClassPathResource("db/migration/V4__assignments.sql"), StandardCharsets.UTF_8);
			ScriptUtils.executeSqlScript(conn, resource);
		} catch (Exception e) {
			throw new IllegalStateException("Assignment schema initialization failed", e);
		}
	}

	public List<Assignment> list() {
		return dsl.selectFrom(ASSIGNMENTS).orderBy(A_CREATED_AT.desc()).fetch(this::toAssignment);
	}

	public Assignment require(String id) {
		return find(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Assignment not found"));
	}

	private Optional<Assignment> find(String id) {
		return dsl.selectFrom(ASSIGNMENTS).where(A_ID.eq(id)).fetchOptional(this::toAssignment);
	}

	public Assignment create(Assignment input, String hostEmail) {
		String quizId = input.getQuizId() == null ? "" : input.getQuizId().trim();
		if (quizId.isEmpty())
			throw fail(HttpStatus.BAD_REQUEST, "quizId is required");
		Integer known = dsl.selectCount().from(Tables.QUIZZES).where(Tables.QUIZZES_ID.eq(quizId)).fetchOne(0,
				Integer.class);
		if (known == null || known == 0)
			throw fail(HttpStatus.NOT_FOUND, "Quiz not found");
		String title = input.getTitle() == null ? "" : input.getTitle().trim();
		if (title.isEmpty() || title.length() > 200)
			throw fail(HttpStatus.BAD_REQUEST, "Title must be 1-200 characters");
		QuestionConfigValidator.requireCleanText("title", title);
		QuestionConfigValidator.requireCleanText("description", input.getDescription());
		Long release = input.getReleaseAt();
		Long due = input.getDueAt();
		if (release != null && due != null && release >= due)
			throw fail(HttpStatus.BAD_REQUEST, "releaseAt must be before dueAt");

		String id = Ids.uuid();
		long now = Instant.now().getEpochSecond();
		String host = hostEmail == null || hostEmail.isBlank() ? "system@local" : hostEmail;
		dsl.insertInto(ASSIGNMENTS)
				.columns(A_ID, A_QUIZ_ID, A_TITLE, A_DESC, A_RELEASE, A_DUE, A_CREATED_BY, A_CREATED_AT,
						A_LTI_DEPLOYMENT)
				.values(id, quizId, title, blankToNull(input.getDescription()), release, due, host, now,
						blankToNull(input.getLtiDeploymentId()))
				.execute();
		return require(id);
	}

	/** Total points available in the assignment's quiz, used as the default max. */
	public int defaultMaxScore(String assignmentId) {
		return totalPoints(require(assignmentId).getQuizId());
	}

	public List<RosterStudent> roster(String assignmentId) {
		require(assignmentId);
		return dsl.selectFrom(ROSTER).where(R_ASSIGNMENT.eq(assignmentId)).orderBy(R_NAME.asc())
				.fetch(this::toRosterStudent);
	}

	/** Imports a CSV roster. Columns: student_id,name,email (name/email optional). */
	public int importRoster(String assignmentId, String csv) {
		require(assignmentId);
		if (csv == null || csv.isBlank())
			throw fail(HttpStatus.BAD_REQUEST, "csv is required");
		if (csv.length() > MAX_CSV_BYTES)
			throw fail(HttpStatus.BAD_REQUEST, "Roster too large (max 1MB)");
		String[] lines = csv.split("\\R");
		if (lines.length == 0)
			throw fail(HttpStatus.BAD_REQUEST, "Roster is empty");

		String[] header = parseCsvLine(stripBom(lines[0])).toArray(new String[0]);
		int studentCol = columnIndex(header, "student_id", "studentid", "id");
		if (studentCol < 0)
			throw fail(HttpStatus.BAD_REQUEST, "CSV needs a student_id column");
		int nameCol = columnIndex(header, "name", "student_name");
		int emailCol = columnIndex(header, "email");

		int imported = 0;
		for (int i = 1; i < lines.length && imported < MAX_ROSTER_ROWS; i++) {
			if (lines[i].isBlank())
				continue;
			List<String> cells = parseCsvLine(lines[i]);
			String studentId = cell(cells, studentCol);
			if (studentId.isEmpty())
				continue;
			String name = cell(cells, nameCol);
			if (name.isEmpty())
				name = studentId;
			String email = cell(cells, emailCol);
			saveRosterStudent(assignmentId, studentId, name, email.isEmpty() ? null : email);
			imported++;
		}
		return imported;
	}

	private void saveRosterStudent(String assignmentId, String studentId, String name, String email) {
		Integer known = dsl.selectCount().from(ROSTER)
				.where(R_ASSIGNMENT.eq(assignmentId).and(R_STUDENT.eq(studentId))).fetchOne(0, Integer.class);
		if (known != null && known > 0) {
			dsl.update(ROSTER).set(R_NAME, name).set(R_EMAIL, email)
					.where(R_ASSIGNMENT.eq(assignmentId).and(R_STUDENT.eq(studentId))).execute();
			return;
		}
		dsl.insertInto(ROSTER).columns(R_ID, R_ASSIGNMENT, R_STUDENT, R_NAME, R_EMAIL)
				.values(Ids.uuid(), assignmentId, studentId, name, email).execute();
	}

	public List<StudentProgress> progress(String assignmentId) {
		require(assignmentId);
		return dsl.selectFrom(PROGRESS).where(P_ASSIGNMENT.eq(assignmentId)).orderBy(P_STUDENT.asc())
				.fetch(this::toProgress);
	}

	/** Starts (or resumes) a student's work, enforcing release/due and roster rules. */
	public StudentProgress start(String assignmentId, String studentId) {
		Assignment assignment = require(assignmentId);
		long now = Instant.now().getEpochSecond();
		if (assignment.getReleaseAt() != null && assignment.getReleaseAt() > now)
			throw fail(HttpStatus.FORBIDDEN, "Assignment is not released yet");
		if (assignment.getDueAt() != null && assignment.getDueAt() < now)
			throw fail(HttpStatus.FORBIDDEN, "Assignment is closed");
		String student = requireStudent(studentId);
		requireRosterMember(assignmentId, student);

		Optional<StudentProgress> existing = findProgress(assignmentId, student);
		if (existing.isPresent() && !"NOT_STARTED".equals(existing.get().getStatus()))
			return existing.get();
		return saveProgress(assignmentId, student, "IN_PROGRESS", now, null, null, totalPoints(assignment.getQuizId()),
				null);
	}

	public StudentProgress studentProgress(String assignmentId, String studentId) {
		require(assignmentId);
		String student = requireStudent(studentId);
		return findProgress(assignmentId, student).orElseGet(() -> new StudentProgress("", assignmentId, student,
				"NOT_STARTED", null, null, null, null, null));
	}

	/** Records a grade and returns the generic grade-passback payload. */
	public GradeResult recordGrade(String assignmentId, String studentId, int score, int maxScore) {
		Assignment assignment = require(assignmentId);
		String student = requireStudent(studentId);
		requireRosterMember(assignmentId, student);
		if (maxScore <= 0 || score < 0 || score > maxScore)
			throw fail(HttpStatus.BAD_REQUEST, "score must be between 0 and maxScore (maxScore > 0)");
		long now = Instant.now().getEpochSecond();
		StudentProgress current = findProgress(assignmentId, student).orElse(null);
		Long startedAt = current == null || current.getStartedAt() == null ? now : current.getStartedAt();
		Long submittedAt = current == null || current.getSubmittedAt() == null ? now : current.getSubmittedAt();
		saveProgress(assignmentId, student, "GRADED", startedAt, submittedAt, score, maxScore, now);

		String grade = (score * 100 / maxScore) + "%";
		String payload = Json.write(Map.of("userId", student, "activityId", assignmentId, "scoreMaximum", maxScore,
				"scoreGiven", score, "gradingProgress", "FullyGraded", "timestamp", now));
		return new GradeResult(assignmentId, student, score, maxScore, grade, payload);
	}

	/**
	 * LTI 1.3 launch validation stub: checks the required claims but does NOT
	 * verify the OIDC state/nonce or the platform JWT signature. The returned
	 * signedLaunch is a labelled placeholder, never a usable credential.
	 */
	public Map<String, Object> validateLtiLaunch(Map<String, ?> claims) {
		if (claims == null)
			throw fail(HttpStatus.BAD_REQUEST, "Launch claims are required");
		String issuer = requiredClaim(claims, "iss");
		String audience = requiredClaim(claims, "aud");
		String subject = requiredClaim(claims, "sub");
		String deployment = requiredClaim(claims, "deployment_id");

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("valid", Boolean.TRUE);
		result.put("issuer", issuer);
		result.put("audience", audience);
		result.put("subject", subject);
		result.put("deploymentId", deployment);
		String context = asText(claims.get("context_id"));
		if (context != null)
			result.put("contextId", context);
		String payload = Json.write(Map.of("iss", issuer, "aud", audience, "sub", subject, "deployment_id", deployment));
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		result.put("signedLaunch", "PLACEHOLDER." + encoded + ".UNSIGNED");
		return result;
	}

	private String requiredClaim(Map<String, ?> claims, String name) {
		String value = asText(claims.get(name));
		if (value == null || value.isEmpty() || value.length() > 512)
			throw fail(HttpStatus.BAD_REQUEST, "Invalid or missing launch claim: " + name);
		return value;
	}

	private @Nullable String asText(Object value) {
		if (value == null)
			return null;
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			return list.isEmpty() ? null : asText(list.get(0));
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? null : text;
	}

	private StudentProgress saveProgress(String assignmentId, String student, String status, Long startedAt,
			Long submittedAt, Integer score, Integer maxScore, Long gradedAt) {
		Optional<StudentProgress> existing = findProgress(assignmentId, student);
		if (existing.isPresent()) {
			String id = existing.get().getId();
			dsl.update(PROGRESS).set(P_STATUS, status).set(P_STARTED, startedAt).set(P_SUBMITTED, submittedAt)
					.set(P_SCORE, score).set(P_MAX_SCORE, maxScore).set(P_GRADED, gradedAt).where(P_ID.eq(id))
					.execute();
		} else {
			dsl.insertInto(PROGRESS)
					.columns(P_ID, P_ASSIGNMENT, P_STUDENT, P_STATUS, P_STARTED, P_SUBMITTED, P_SCORE, P_MAX_SCORE,
							P_GRADED)
					.values(Ids.uuid(), assignmentId, student, status, startedAt, submittedAt, score, maxScore,
							gradedAt)
					.execute();
		}
		return findProgress(assignmentId, student).orElseThrow();
	}

	private Optional<StudentProgress> findProgress(String assignmentId, String student) {
		return dsl.selectFrom(PROGRESS).where(P_ASSIGNMENT.eq(assignmentId).and(P_STUDENT.eq(student)))
				.fetchOptional(this::toProgress);
	}

	private int totalPoints(String quizId) {
		List<Integer> points = dsl.select(Tables.QUESTIONS_POINTS).from(Tables.QUESTIONS)
				.where(Tables.QUESTIONS_QUIZ_ID.eq(quizId)).fetch(Tables.QUESTIONS_POINTS);
		int total = 0;
		for (Integer point : points) {
			if (point != null)
				total += point;
		}
		return total;
	}

	private void requireRosterMember(String assignmentId, String student) {
		Integer known = dsl.selectCount().from(ROSTER)
				.where(R_ASSIGNMENT.eq(assignmentId).and(R_STUDENT.eq(student))).fetchOne(0, Integer.class);
		if (known == null || known == 0)
			throw fail(HttpStatus.FORBIDDEN, "Student is not in this assignment roster");
	}

	private String requireStudent(String studentId) {
		String student = studentId == null ? "" : studentId.trim();
		if (student.isEmpty() || student.length() > 200)
			throw fail(HttpStatus.BAD_REQUEST, "studentId is required");
		return student;
	}

	private static ResponseStatusException fail(HttpStatus status, String message) {
		return new ResponseStatusException(status, message);
	}

	private static @Nullable String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String stripBom(String value) {
		return value.startsWith("\ufeff") ? value.substring(1) : value;
	}

	private static List<String> parseCsvLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (c == ',' && !quoted) {
				cells.add(cell.toString().trim());
				cell.setLength(0);
			} else {
				cell.append(c);
			}
		}
		cells.add(cell.toString().trim());
		return cells;
	}

	private static int columnIndex(String[] header, String... candidates) {
		for (int i = 0; i < header.length; i++) {
			for (String candidate : candidates) {
				if (candidate.equals(header[i].toLowerCase(Locale.ROOT)))
					return i;
			}
		}
		return -1;
	}

	private static String cell(List<String> cells, int index) {
		return index >= 0 && index < cells.size() ? cells.get(index) : "";
	}

	private Assignment toAssignment(Record r) {
		return new Assignment(r.get(A_ID), r.get(A_QUIZ_ID), r.get(A_TITLE), r.get(A_DESC),
				RepoUtil.asLongBoxed(r.get(A_RELEASE)), RepoUtil.asLongBoxed(r.get(A_DUE)), r.get(A_CREATED_BY),
				RepoUtil.asLong(r.get(A_CREATED_AT)), r.get(A_LTI_DEPLOYMENT));
	}

	private RosterStudent toRosterStudent(Record r) {
		return new RosterStudent(r.get(R_ID), r.get(R_ASSIGNMENT), r.get(R_STUDENT), r.get(R_NAME), r.get(R_EMAIL));
	}

	private StudentProgress toProgress(Record r) {
		return new StudentProgress(r.get(P_ID), r.get(P_ASSIGNMENT), r.get(P_STUDENT), r.get(P_STATUS),
				RepoUtil.asLongBoxed(r.get(P_STARTED)), RepoUtil.asLongBoxed(r.get(P_SUBMITTED)), r.get(P_SCORE),
				r.get(P_MAX_SCORE), RepoUtil.asLongBoxed(r.get(P_GRADED)));
	}
}
