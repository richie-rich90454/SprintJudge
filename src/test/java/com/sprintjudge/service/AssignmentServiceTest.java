package com.sprintjudge.service;

import com.sprintjudge.PlatformDb;
import com.sprintjudge.domain.assignment.Assignment;
import com.sprintjudge.domain.assignment.GradeResult;
import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AssignmentServiceTest {

  private PlatformDb.Db db;
  private AssignmentService service;

  @BeforeEach
  void setUp() throws Exception {
    db = PlatformDb.open();
    service = new AssignmentService(db.dsl, db.dataSource);
    service.initSchema();
    db.dsl.execute("INSERT INTO quizzes (id, title) VALUES ('q1','Quiz One')");
    db.dsl.execute("INSERT INTO questions (id, quiz_id, title, points_base) VALUES "
        + "('quest1','q1','Q1',10), ('quest2','q1','Q2',20), ('quest3','q1','Q3',NULL)");
  }

  private static Assignment input(String title, String description, Long release, Long due) {
    return new Assignment(null, "q1", title, description, release, due, null, null, null);
  }

  private Assignment create(String title) {
    return service.create(input(title, "desc", null, null), "host@x.y");
  }

  private void addStudent(String assignmentId, String student) {
    service.importRoster(assignmentId, "student_id,name,email\n" + student + ",Name,email@x.y\n");
  }

  private static int status(Runnable action) {
    return assertThrows(ResponseStatusException.class, action::run).getStatusCode().value();
  }

  @Test
  void schemaFailureIsWrapped() throws Exception {
    DataSource broken = mock(DataSource.class);
    when(broken.getConnection()).thenThrow(new SQLException("boom"));
    assertThrows(IllegalStateException.class, () -> new AssignmentService(db.dsl, broken).initSchema());
  }

  @Test
  void listRequireAndCreateDefaults() {
    Assignment first = create("First");
    Assignment second = service.create(input("Second", "  ", null, null), null);
    assertEquals(2, service.list().size());
    assertEquals(first.getId(), service.require(first.getId()).getId());
    assertEquals("host@x.y", first.getCreatedBy());
    assertEquals("system@local", second.getCreatedBy());
    assertEquals("desc", first.getDescription());
    assertEquals(null, second.getDescription());
    assertEquals(null, second.getLtiDeploymentId());
    assertTrue(first.getCreatedAt() > 0);
    assertEquals(404, status(() -> service.require("missing")));
  }

  @Test
  void createValidatesEveryInput() {
    assertEquals(400, status(() -> service.create(new Assignment(null, null, "T", null, null, null, null, null, null),
        "h")));
    assertEquals(400, status(() -> service.create(
        new Assignment(null, "   ", "T", null, null, null, null, null, null), "h")));
    assertEquals(404, status(() -> service.create(
        new Assignment(null, "missing", "T", null, null, null, null, null, null), "h")));
    assertEquals(400, status(() -> service.create(input(null, null, null, null), "h")));
    assertEquals(400, status(() -> service.create(input("   ", null, null, null), "h")));
    assertEquals(400, status(() -> service.create(input("x".repeat(201), null, null, null), "h")));
    assertEquals(400, status(() -> service.create(input("<script>x</script>", null, null, null), "h")));
    assertEquals(400, status(() -> service.create(input("T", "javascript:alert(1)", null, null), "h")));
    assertEquals(400, status(() -> service.create(input("T", null, 20L, 10L), "h")));
    assertEquals(400, status(() -> service.create(input("T", null, 10L, 10L), "h")));
    Assignment valid = service.create(new Assignment(null, "q1", "T", "D", null, null, null, null, "dep"), "h@x.y");
    assertEquals("dep", valid.getLtiDeploymentId());
  }

  @Test
  void defaultMaxScoreSumsNullablePoints() {
    Assignment assignment = create("Scored");
    assertEquals(30, service.defaultMaxScore(assignment.getId()));
    assertEquals(404, status(() -> service.defaultMaxScore("missing")));
  }

  @Test
  void rosterImportCoversCsvBranches() {
    Assignment assignment = create("Roster");
    assertEquals(400, status(() -> service.importRoster(assignment.getId(), null)));
    assertEquals(400, status(() -> service.importRoster(assignment.getId(), "  ")));
    assertEquals(400, status(() -> service.importRoster(assignment.getId(), "x".repeat(1_000_001))));
    assertEquals(400, status(() -> service.importRoster(assignment.getId(), "\u0085")));
    assertEquals(400, status(() -> service.importRoster(assignment.getId(), "name,email\nA,a@x.y\n")));
    assertEquals(404, status(() -> service.importRoster("missing", "student_id\ns1\n")));

    String csv = "\ufeffstudent_id,name,email\n"
        + "\"s1\",\"Doe, Jane\",jane@x.y\n"
        + "s2,,\n"
        + "\n"
        + ",NoId,noid@x.y\n"
        + "s3,\"O\"\"Brien\",\n"
        + "s4,Plain,plain@x.y\n";
    assertEquals(4, service.importRoster(assignment.getId(), csv));
    List<RosterStudent> roster = service.roster(assignment.getId());
    assertEquals(4, roster.size());
    Map<String, RosterStudent> byStudent = new java.util.HashMap<>();
    for (RosterStudent student : roster) {
      byStudent.put(student.getStudentId(), student);
    }
    assertEquals("Doe, Jane", byStudent.get("s1").getName());
    assertEquals("s2", byStudent.get("s2").getName());
    assertEquals("O\"Brien", byStudent.get("s3").getName());
    assertEquals(null, byStudent.get("s3").getEmail());
    assertEquals(4, service.importRoster(assignment.getId(), csv));
    assertEquals(4, service.roster(assignment.getId()).size());
  }

  @Test
  void rosterImportStopsAtRowLimit() {
    Assignment assignment = create("Big roster");
    StringBuilder csv = new StringBuilder("student_id,name\n");
    for (int i = 0; i < 5001; i++) {
      csv.append("s").append(i).append(",Name\n");
    }
    assertEquals(5000, service.importRoster(assignment.getId(), csv.toString()));
  }

  @Test
  void startEnforcesWindowsRosterAndProgressStates() {
    long now = System.currentTimeMillis() / 1000;
    Assignment future = service.create(input("Future", null, now + 3600, null), "h");
    addStudent(future.getId(), "s1");
    assertEquals(403, status(() -> service.start(future.getId(), "s1")));
    Assignment closed = service.create(input("Closed", null, null, now - 3600), "h");
    addStudent(closed.getId(), "s1");
    assertEquals(403, status(() -> service.start(closed.getId(), "s1")));

    Assignment assignment = create("Live");
    addStudent(assignment.getId(), "s1");
    assertEquals(400, status(() -> service.start(assignment.getId(), null)));
    assertEquals(400, status(() -> service.start(assignment.getId(), "  ")));
    assertEquals(400, status(() -> service.start(assignment.getId(), "x".repeat(201))));
    assertEquals(403, status(() -> service.start(assignment.getId(), "not-on-roster")));
    assertEquals(404, status(() -> service.start("missing", "s1")));

    assertEquals("NOT_STARTED", service.studentProgress(assignment.getId(), "s1").getStatus());
    StudentProgress started = service.start(assignment.getId(), "s1");
    assertEquals("IN_PROGRESS", started.getStatus());
    assertEquals(30, started.getMaxScore());
    assertEquals("IN_PROGRESS", service.start(assignment.getId(), "s1").getStatus());

    Assignment resumed = create("Resume");
    addStudent(resumed.getId(), "s2");
    db.dsl.execute("INSERT INTO assignment_progress (id, assignment_id, student_id, status) "
        + "VALUES ('r1', ?, 's2', 'NOT_STARTED')", resumed.getId());
    assertEquals("IN_PROGRESS", service.start(resumed.getId(), "s2").getStatus());

    Assignment graded = create("Graded");
    addStudent(graded.getId(), "s3");
    service.recordGrade(graded.getId(), "s3", 15, 30);
    assertEquals("GRADED", service.start(graded.getId(), "s3").getStatus());
    assertEquals(1, service.progress(graded.getId()).size());
  }

  @Test
  void recordGradeValidatesAndBuildsPassback() {
    Assignment assignment = create("Grades");
    addStudent(assignment.getId(), "s1");
    assertEquals(404, status(() -> service.recordGrade("missing", "s1", 1, 2)));
    assertEquals(400, status(() -> service.recordGrade(assignment.getId(), null, 1, 2)));
    assertEquals(403, status(() -> service.recordGrade(assignment.getId(), "stranger", 1, 2)));
    assertEquals(400, status(() -> service.recordGrade(assignment.getId(), "s1", 1, 0)));
    assertEquals(400, status(() -> service.recordGrade(assignment.getId(), "s1", -1, 10)));
    assertEquals(400, status(() -> service.recordGrade(assignment.getId(), "s1", 11, 10)));

    GradeResult result = service.recordGrade(assignment.getId(), "s1", 3, 4);
    assertEquals("75%", result.getGrade());
    assertEquals(assignment.getId(), result.getAssignmentId());
    assertEquals("s1", result.getStudentId());
    assertEquals(3, result.getScore());
    assertEquals(4, result.getMaxScore());
    assertTrue(result.getPassbackPayload().contains("FullyGraded"));

    db.dsl.execute("INSERT INTO assignment_progress (id, assignment_id, student_id, status) "
        + "VALUES ('r2', ?, 's2', 'IN_PROGRESS')", assignment.getId());
    addStudent(assignment.getId(), "s2");
    assertNotNull(service.recordGrade(assignment.getId(), "s2", 1, 2));
    db.dsl.execute("INSERT INTO assignment_progress (id, assignment_id, student_id, status, started_at, submitted_at) "
        + "VALUES ('r3', ?, 's3', 'IN_PROGRESS', 100, 200)", assignment.getId());
    addStudent(assignment.getId(), "s3");
    assertNotNull(service.recordGrade(assignment.getId(), "s3", 1, 2));
    StudentProgress progress = service.studentProgress(assignment.getId(), "s3");
    assertEquals(100L, progress.getStartedAt());
    assertEquals(200L, progress.getSubmittedAt());
  }

  @Test
  void ltiLaunchValidatesClaims() {
    assertEquals(400, status(() -> service.validateLtiLaunch(null)));
    assertEquals(400, status(() -> service.validateLtiLaunch(Map.of("aud", "a", "sub", "s", "deployment_id", "d"))));
    assertEquals(400, status(() -> service.validateLtiLaunch(
        Map.of("iss", "", "aud", "a", "sub", "s", "deployment_id", "d"))));
    assertEquals(400, status(() -> service.validateLtiLaunch(
        Map.of("iss", "x".repeat(513), "aud", "a", "sub", "s", "deployment_id", "d"))));
    assertEquals(400, status(() -> service.validateLtiLaunch(
        Map.of("iss", List.of(), "aud", "a", "sub", "s", "deployment_id", "d"))));

    Map<String, Object> claims = Map.of("iss", "issuer", "aud", List.of("aud1"), "sub", 42,
        "deployment_id", "dep", "context_id", "ctx");
    Map<String, Object> result = service.validateLtiLaunch(claims);
    assertEquals(true, result.get("valid"));
    assertEquals("issuer", result.get("issuer"));
    assertEquals("aud1", result.get("audience"));
    assertEquals("42", result.get("subject"));
    assertEquals("dep", result.get("deploymentId"));
    assertEquals("ctx", result.get("contextId"));
    String signed = String.valueOf(result.get("signedLaunch"));
    assertTrue(signed.startsWith("PLACEHOLDER."));
    assertTrue(signed.endsWith(".UNSIGNED"));
    assertFalse(service.validateLtiLaunch(Map.of("iss", "i", "aud", "a", "sub", "s", "deployment_id", "d"))
        .containsKey("contextId"));
  }
}
