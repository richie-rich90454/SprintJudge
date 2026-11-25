package com.sprintjudge.controller;

import com.sprintjudge.domain.assignment.Assignment;
import com.sprintjudge.domain.assignment.GradeResult;
import com.sprintjudge.domain.assignment.RosterStudent;
import com.sprintjudge.domain.assignment.StudentProgress;
import com.sprintjudge.service.AssignmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AssignmentControllerTest {

	private final AssignmentService service = mock(AssignmentService.class);
	private final AssignmentController controller = new AssignmentController(service);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void delegatesReadAndRosterEndpoints() {
		Assignment assignment = new Assignment();
		when(service.list()).thenReturn(List.of(assignment));
		when(service.require("a1")).thenReturn(assignment);
		when(service.roster("a1")).thenReturn(List.of(new RosterStudent()));
		when(service.progress("a1")).thenReturn(List.of(new StudentProgress()));
		when(service.importRoster("a1", "s1,u\n")).thenReturn(1);
		when(service.start("a1", "s1")).thenReturn(new StudentProgress());
		when(service.studentProgress("a1", "s1")).thenReturn(new StudentProgress());

		assertEquals(1, controller.list().size());
		assertEquals(assignment, controller.get("a1"));
		assertEquals(1, controller.roster("a1").size());
		assertEquals(1, controller.progress("a1").size());
		assertEquals(1, controller.importRoster("a1", Map.of("csv", "s1,u\n")).get("imported"));
		assertEquals(assignment.getId(), controller.publicGet("a1").getId());
		assertNotNull(controller.start("a1", Map.of("studentId", "s1")));
		assertNotNull(controller.studentProgress("a1", "s1"));
		when(service.validateLtiLaunch(any())).thenReturn(Map.of("valid", true));
		assertEquals(true, controller.ltiLaunch(Map.of("iss", "x")).get("valid"));
	}

	@Test
	void publicGetExposesOnlyLaunchFields() throws Exception {
		Assignment a = new Assignment("a1", "secret-quiz", "Title", "Desc", 100L, 200L, "owner@x.y", 1L, "lti-1");
		when(service.require("a1")).thenReturn(a);
		AssignmentController.PublicAssignment dto = controller.publicGet("a1");
		String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);
		assertEquals("a1", dto.getId());
		assertEquals("Title", dto.getTitle());
		assertTrue(json.contains("\"title\":\"Title\""), json);
		for (String leaked : List.of("quizId", "createdBy", "ltiDeploymentId", "releaseAt", "dueAt", "secret-quiz",
				"owner@x.y", "lti-1", "100", "200"))
			assertFalse(json.contains(leaked), json);
	}

	@Test
	void publicAssignmentValueMethods() {
		AssignmentController.PublicAssignment dto = new AssignmentController.PublicAssignment();
		dto.setId("x");
		dto.setTitle("t");
		dto.setDescription("d");
		assertEquals("x", dto.getId());
		assertEquals("t", dto.getTitle());
		assertEquals("d", dto.getDescription());
	}

	@Test
	void createResolvesHostFromSessionOnly() {
		Assignment body = new Assignment();
		when(service.create(any(), anyString())).thenReturn(body);
		assertEquals(body, controller.create(body));
		verify(service).create(body, "system@local");

		assertEquals("host@x.y", hostFor("host@x.y"));
		assertEquals("system@local", hostFor("   "));
		assertEquals("system@local", hostFor(new Object()));
		assertEquals("u@x.y", hostFor(User.withUsername("u@x.y").password("p").roles("ADMIN").build()));
		assertEquals("oauth@x.y", hostFor(oauth("oauth@x.y")));
		assertEquals("system@local", hostFor(oauth(null)));
		assertEquals("system@local", hostFor(oauth("  ")));
	}

	private String hostFor(Object principal) {
		org.mockito.Mockito.clearInvocations(service);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "p"));
		ArgumentCaptor<String> host = ArgumentCaptor.forClass(String.class);
		controller.create(new Assignment());
		verify(service).create(any(), host.capture());
		SecurityContextHolder.clearContext();
		return host.getValue();
	}

	private static OAuth2User oauth(String email) {
		OAuth2User user = mock(OAuth2User.class);
		when(user.getAttribute("email")).thenReturn(email);
		return user;
	}

	@Test
	void recordGradeParsesAndDefaultsMax() {
		assertEquals(400,
				assertThrows(ResponseStatusException.class, () -> controller.recordGrade("a1", Map.of("score", 1)))
						.getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class,
				() -> controller.recordGrade("a1", Map.of("studentId", "s1"))).getStatusCode().value());

		when(service.defaultMaxScore("a1")).thenReturn(50);
		when(service.recordGrade(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyInt())).thenReturn(new GradeResult());
		assertNotNull(controller.recordGrade("a1", Map.of("studentId", "s1", "score", 10)));
		verify(service).recordGrade("a1", "s1", 10, 50);

		assertNotNull(controller.recordGrade("a1", Map.of("studentId", "s1", "score", "20", "maxScore", "80")));
		verify(service).recordGrade("a1", "s1", 20, 80);

		assertNotNull(
				controller.recordGrade("a1", Map.of("studentId", "s1", "score", 5, "maxScore", Integer.valueOf(25))));
		verify(service).recordGrade("a1", "s1", 5, 25);

		assertEquals(400,
				assertThrows(ResponseStatusException.class,
						() -> controller.recordGrade("a1", Map.of("studentId", "s1", "score", "x"))).getStatusCode()
						.value());
		assertEquals(400,
				assertThrows(ResponseStatusException.class,
						() -> controller.recordGrade("a1", Map.of("studentId", "s1", "score", 1, "maxScore", "x")))
						.getStatusCode().value());
	}
}
