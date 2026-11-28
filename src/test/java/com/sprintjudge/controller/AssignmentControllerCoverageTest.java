package com.sprintjudge.controller;

import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.service.AssignmentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AssignmentControllerCoverageTest {

	private final AssignmentService service = mock(AssignmentService.class);
	private final AssignmentController controller = new AssignmentController(service);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticate(Object principal, String role) {
		Authentication auth = new UsernamePasswordAuthenticationToken(principal, "p",
				List.of(new SimpleGrantedAuthority("ROLE_" + role)));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private void roster(String... studentIds) {
		List<RosterStudent> rows = new java.util.ArrayList<>();
		for (String studentId : studentIds) {
			rows.add(new RosterStudent("r", "a1", studentId, studentId, null));
		}
		when(service.roster("a1")).thenReturn(rows);
	}

	private static OAuth2User oauth(String email) {
		OAuth2User user = mock(OAuth2User.class);
		when(user.getAttribute("email")).thenReturn(email);
		return user;
	}

	@Test
	void blankOrMissingStudentIdIsForbiddenBeforeAnyServiceCall() {
		authenticate("s1", "USER");
		assertEquals(403, assertThrows(ResponseStatusException.class,
				() -> controller.startForCaller("a1", Map.of("studentId", ""))).getStatusCode().value());
		assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.startForCaller("a1", Map.of()))
				.getStatusCode().value());
		Map<String, String> nullBody = new HashMap<>();
		nullBody.put("studentId", null);
		assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.startForCaller("a1", nullBody))
				.getStatusCode().value());
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "  "))
						.getStatusCode().value());
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", null))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void unauthenticatedNonAnonymousTokenIsForbidden() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("s1", "p"));
		assertEquals(403, assertThrows(ResponseStatusException.class,
				() -> controller.startForCaller("a1", Map.of("studentId", "s1"))).getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void oauthPrincipalWithMatchingEmailReadsOwnProgress() {
		authenticate(oauth("s1"), "USER");
		roster("s1");
		StudentProgress progress = new StudentProgress();
		when(service.studentProgress("a1", "s1")).thenReturn(progress);
		assertEquals(progress, controller.studentProgressForCaller("a1", "s1"));
	}

	@Test
	void oauthPrincipalWithNullOrBlankEmailIsForbidden() {
		authenticate(oauth(null), "USER");
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		SecurityContextHolder.clearContext();
		authenticate(oauth("  "), "USER");
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void stringPrincipalCoversBlankAndNonBlankNames() {
		authenticate("s1", "USER");
		roster("s1");
		StudentProgress progress = new StudentProgress();
		when(service.start("a1", "s1")).thenReturn(progress);
		assertEquals(progress, controller.startForCaller("a1", Map.of("studentId", "s1")));

		SecurityContextHolder.clearContext();
		authenticate("  ", "USER");
		assertEquals(403, assertThrows(ResponseStatusException.class,
				() -> controller.startForCaller("a1", Map.of("studentId", "s1"))).getStatusCode().value());
	}

	@Test
	void unrecognizedPrincipalIsForbidden() {
		authenticate(new Object(), "USER");
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}
}
