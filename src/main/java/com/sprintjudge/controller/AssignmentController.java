package com.sprintjudge.controller;

import com.sprintjudge.domain.assignment.Assignment;
import com.sprintjudge.domain.assignment.GradeResult;
import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.service.AssignmentService;
import jakarta.validation.Valid;
import org.eclipse.jdt.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Assignment REST surface. Admin paths inherit the /api/admin/** ADMIN rule;
 * the student launch/start/progress paths sit under the public permitAll rule
 * and are gated by roster membership and release windows inside the service.
 */
@RestController
public class AssignmentController {

	private final AssignmentService assignments;

	public AssignmentController(AssignmentService assignments) {
		this.assignments = assignments;
	}

	@PostMapping("/api/admin/assignments")
	public Assignment create(@Valid @RequestBody Assignment body) {
		return assignments.create(body, hostEmail());
	}

	@GetMapping("/api/admin/assignments")
	public List<Assignment> list() {
		return assignments.list();
	}

	@GetMapping("/api/admin/assignments/{id}")
	public Assignment get(@PathVariable String id) {
		return assignments.require(id);
	}

	@GetMapping("/api/admin/assignments/{id}/roster")
	public List<RosterStudent> roster(@PathVariable String id) {
		return assignments.roster(id);
	}

	@PostMapping("/api/admin/assignments/{id}/roster")
	public Map<String, Object> importRoster(@PathVariable String id, @RequestBody Map<String, String> body) {
		String csv = body.get("csv");
		int imported = assignments.importRoster(id, csv);
		return Map.of("imported", imported);
	}

	@GetMapping("/api/admin/assignments/{id}/progress")
	public List<StudentProgress> progress(@PathVariable String id) {
		return assignments.progress(id);
	}

	/** Records a grade and returns the LMS grade-passback payload. */
	@PostMapping("/api/admin/assignments/{id}/grades")
	public GradeResult recordGrade(@PathVariable String id, @RequestBody Map<String, Object> body) {
		Object student = body.get("studentId");
		Object score = body.get("score");
		Object maxScore = body.get("maxScore");
		if (student == null || score == null)
			throw bad("studentId and score are required");
		int max = maxScore == null ? assignments.defaultMaxScore(id) : number(maxScore, "maxScore");
		return assignments.recordGrade(id, String.valueOf(student), number(score, "score"), max);
	}

	@GetMapping("/api/public/assignments/{id}")
	public PublicAssignment publicGet(@PathVariable String id, @RequestParam(required = false) String studentId) {
		// Same gate as start/progress: an arbitrary id is not enough to read
		// assignment metadata anonymously.
		requireRosterStudent(id, studentId);
		return toPublic(assignments.require(id));
	}

	// Non-HTTP callers keep the original delegation semantics.
	PublicAssignment publicGet(String id) {
		return toPublic(assignments.require(id));
	}

	private static PublicAssignment toPublic(Assignment assignment) {
		return new PublicAssignment(assignment.getId(), assignment.getTitle(), assignment.getDescription());
	}

	/**
	 * Launch metadata only: quiz id, creator, LTI deployment and windows stay
	 * admin-only.
	 */
	public static final class PublicAssignment {
		private String id;
		private String title;
		private @Nullable String description;

		@SuppressWarnings("all")
		public PublicAssignment() {
		}

		public PublicAssignment(String id, String title, @Nullable String description) {
			this.id = id;
			this.title = title;
			this.description = description;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public @Nullable String getDescription() {
			return description;
		}

		public void setDescription(@Nullable String description) {
			this.description = description;
		}
	}

	@PostMapping("/api/public/assignments/{id}/start")
	public StudentProgress startForCaller(@PathVariable String id, @RequestBody Map<String, String> body) {
		return assignments.start(id, requireRosterStudent(id, body.get("studentId")));
	}

	@GetMapping("/api/public/assignments/{id}/progress")
	public StudentProgress studentProgressForCaller(@PathVariable String id, @RequestParam String studentId) {
		return assignments.studentProgress(id, requireRosterStudent(id, studentId));
	}

	/**
	 * LTI 1.3 validation is not implemented: never report an unverified launch as
	 * valid until JWT signature and OIDC state checks exist.
	 */
	@PostMapping("/api/public/assignments/lti/launch")
	public Map<String, Object> ltiLaunchNotImplemented() {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "not_implemented");
	}

	// Non-HTTP callers keep the original delegation semantics.
	StudentProgress start(String id, Map<String, String> body) {
		return assignments.start(id, body.get("studentId"));
	}

	StudentProgress studentProgress(String id, String studentId) {
		return assignments.studentProgress(id, studentId);
	}

	Map<String, Object> ltiLaunch(Map<String, Object> claims) {
		return assignments.validateLtiLaunch(claims);
	}

	/**
	 * The student id from the request is only accepted from an authenticated caller
	 * (an admin may act for any roster student); anonymous callers and mismatched
	 * session identities are rejected.
	 */
	private String requireRosterStudent(String assignmentId, String requestedStudentId) {
		String student = requestedStudentId == null ? "" : requestedStudentId.trim();
		if (student.isEmpty())
			throw forbidden("studentId is required");
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
			throw forbidden("student_identity_required");
		if (!isAdmin(auth)) {
			String caller = principalName(auth);
			if (caller == null || !caller.equals(student))
				throw forbidden("student_identity_mismatch");
		}
		boolean onRoster = assignments.roster(assignmentId).stream()
				.anyMatch(row -> student.equals(row.getStudentId()));
		if (!onRoster)
			throw forbidden("student_not_in_roster");
		return student;
	}

	private static boolean isAdmin(Authentication auth) {
		for (GrantedAuthority authority : auth.getAuthorities()) {
			if ("ROLE_ADMIN".equals(authority.getAuthority()))
				return true;
		}
		return false;
	}

	private static String principalName(Authentication auth) {
		Object principal = auth.getPrincipal();
		if (principal instanceof UserDetails)
			return ((UserDetails) principal).getUsername();
		if (principal instanceof OAuth2User) {
			String email = ((OAuth2User) principal).getAttribute("email");
			return email == null || email.isBlank() ? null : email;
		}
		if (principal instanceof String) {
			String name = (String) principal;
			return name.isBlank() ? null : name;
		}
		return null;
	}

	private static ResponseStatusException forbidden(String reason) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
	}

	private static int number(Object value, String field) {
		if (value instanceof Number)
			return ((Number) value).intValue();
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			throw bad(field + " must be a number");
		}
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	/**
	 * Host attribution is resolved from the session, never from the request body.
	 */
	private static String hostEmail() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			Object principal = auth.getPrincipal();
			if (principal instanceof OAuth2User) {
				String email = ((OAuth2User) principal).getAttribute("email");
				if (email != null && !email.isBlank())
					return email;
			} else if (principal instanceof UserDetails) {
				return ((UserDetails) principal).getUsername();
			} else if (principal instanceof String) {
				String name = (String) principal;
				if (!name.isBlank())
					return name;
			}
		}
		return "system@local";
	}
}
