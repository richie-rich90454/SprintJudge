package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class WebSocketOriginFilterTest {

	private final WebSocketOriginFilter filter = new WebSocketOriginFilter();

	@Test
	void allowsSameHostAndLoopbackOrigins() {
		assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/public/quizzes")));
		assertEquals(false, filter.shouldNotFilter(new MockHttpServletRequest("GET", "/ws")));
		assertTrue(com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed("http://localhost:5173",
				"127.0.0.1:8080"));
		assertTrue(com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed("https://quiz.example.org",
				"quiz.example.org:443"));
	}

	@Test
	void rejectsForeignAndMalformedOrigins() {
		assertEquals(false, com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed("https://evil.example",
				"quiz.example.org:443"));
		assertEquals(false,
				com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed("not a url", "quiz.example.org"));
		assertEquals(false,
				com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed("https://evil.example", null));
	}

	@Test
	void filterAllowsRequestWithoutOriginOrProxyHeaders() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
		request.setRemoteAddr("8.8.8.8");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
		});
		assertEquals(200, response.getStatus());

		MockHttpServletRequest proxied = new MockHttpServletRequest("GET", "/ws");
		proxied.setRemoteAddr("127.0.0.1");
		proxied.addHeader("X-Real-IP", "1.2.3.4");
		proxied.addHeader("Origin", "http://localhost:5173");
		proxied.addHeader("Host", "127.0.0.1:8080");
		MockHttpServletResponse proxiedResponse = new MockHttpServletResponse();
		filter.doFilter(proxied, proxiedResponse, (req, res) -> {
		});
		assertEquals(200, proxiedResponse.getStatus());
	}

	@Test
	void filterRejectsSpoofedProxyHeadersFromDirectClients() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
		request.setRemoteAddr("8.8.8.8");
		request.addHeader("X-Forwarded-For", "1.2.3.4");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("chain must not run for a spoofed proxy header");
		});
		assertEquals(403, response.getStatus());

		MockHttpServletRequest legit = new MockHttpServletRequest("GET", "/ws");
		legit.setRemoteAddr("127.0.0.1");
		legit.addHeader("X-Forwarded-For", "1.2.3.4");
		MockHttpServletResponse ok = new MockHttpServletResponse();
		filter.doFilter(legit, ok, (req, res) -> {
		});
		assertEquals(200, ok.getStatus());
	}

	@Test
	void filterRejectsSpoofedRealIpFromDirectClients() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
		request.setRemoteAddr("8.8.8.8");
		request.addHeader("X-Real-IP", "1.2.3.4");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("chain must not run for a spoofed proxy header");
		});
		assertEquals(403, response.getStatus());
	}

	@Test
	void filterRejectsForbiddenOriginBeforeUpgrade() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
		request.addHeader("Origin", "https://evil.example");
		request.addHeader("Host", "quiz.example.org");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("chain must not run for a foreign origin");
		});
		assertEquals(403, response.getStatus());
	}
}
