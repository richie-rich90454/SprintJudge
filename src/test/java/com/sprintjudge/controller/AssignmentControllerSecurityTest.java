package com.sprintjudge.controller;

import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.service.AssignmentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AssignmentControllerSecurityTest {

	private final AssignmentService service = mock(AssignmentService.class);
	private final AssignmentController controller = new AssignmentController(service);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticate(String name, String role) {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(
						User.withUsername(name).password("p").roles(role).build(), "p",
						List.of(new SimpleGrantedAuthority("ROLE_" + role))));
	}

	private void roster(String... studentIds) {
		List<RosterStudent> rows = new ArrayList<>();
		for (String studentId : studentIds) {
			rows.add(new RosterStudent("r", "a1", studentId, studentId, null));
		}
		when(service.roster("a1")).thenReturn(rows);
	}

	@Test
	void anonymousStartAndProgressAreForbidden() {
		assertEquals(403, assertThrows(ResponseStatusException.class,
				() -> controller.startForCaller("a1", Map.of("studentId", "s1"))).getStatusCode().value());
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void anonymousAuthenticationTokenIsForbidden() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void mismatchedSessionIdentityIsForbidden() {
		authenticate("other", "USER");
		assertEquals(403, assertThrows(ResponseStatusException.class,
				() -> controller.startForCaller("a1", Map.of("studentId", "s1"))).getStatusCode().value());
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
		verifyNoInteractions(service);
	}

	@Test
	void matchingStudentReadsOwnProgress() {
		authenticate("s1", "USER");
		roster("s1", "s2");
		StudentProgress progress = new StudentProgress();
		when(service.studentProgress("a1", "s1")).thenReturn(progress);
		assertEquals(progress, controller.studentProgressForCaller("a1", "s1"));
		verify(service).studentProgress("a1", "s1");
	}

	@Test
	void studentOutsideRosterIsForbidden() {
		authenticate("s1", "USER");
		roster("s2");
		assertEquals(403,
				assertThrows(ResponseStatusException.class, () -> controller.studentProgressForCaller("a1", "s1"))
						.getStatusCode().value());
	}

	@Test
	void adminMayManageRosterStudent() {
		authenticate("admin", "ADMIN");
		roster("s1");
		when(service.start("a1", "s1")).thenReturn(new StudentProgress());
		assertNotNull(controller.startForCaller("a1", Map.of("studentId", "s1")));
		verify(service).start("a1", "s1");
	}

	@Test
	void ltiLaunchIsNotImplemented() {
		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				controller::ltiLaunchNotImplemented);
		assertEquals(501, error.getStatusCode().value());
		assertTrue(error.getReason().contains("not_implemented"));
		verifyNoInteractions(service);
	}
}
