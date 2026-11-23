package com.sprintjudge.controller;

import com.sprintjudge.domain.assignment.Assignment;
import com.sprintjudge.domain.assignment.GradeResult;
import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
	public Assignment publicGet(@PathVariable String id) {
		return assignments.require(id);
	}

	@PostMapping("/api/public/assignments/{id}/start")
	public StudentProgress start(@PathVariable String id, @RequestBody Map<String, String> body) {
		return assignments.start(id, body.get("studentId"));
	}

	@GetMapping("/api/public/assignments/{id}/progress")
	public StudentProgress studentProgress(@PathVariable String id, @RequestParam String studentId) {
		return assignments.studentProgress(id, studentId);
	}

	/** LTI 1.3 launch stub: validates required claims, returns a signed-launch placeholder. */
	@PostMapping("/api/public/assignments/lti/launch")
	public Map<String, Object> ltiLaunch(@RequestBody Map<String, Object> claims) {
		return assignments.validateLtiLaunch(claims);
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

	/** Host attribution is resolved from the session, never from the request body. */
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
