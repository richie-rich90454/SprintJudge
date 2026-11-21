package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class AdminRequestOriginFilterTest {

	private final AdminRequestOriginFilter filter = new AdminRequestOriginFilter();

	@Test
	void onlyStateChangingAdminRequestsAreChecked() {
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/admin/quizzes")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("HEAD", "/api/admin/quizzes")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("OPTIONS", "/api/admin/quizzes")));
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/public/practice")));
		assertEquals(false, filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/admin/quizzes")));
	}

	@Test
	void sameOriginAndLoopbackAreAllowed() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/quizzes");
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Host", "localhost:8080");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
		});
		assertEquals(200, response.getStatus());
	}

	@Test
	void foreignOriginIsRejected() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/quizzes");
		request.addHeader("Origin", "https://evil.example");
		request.addHeader("Host", "quiz.example.org");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("chain must not run for a foreign origin");
		});
		assertEquals(403, response.getStatus());
	}
}
