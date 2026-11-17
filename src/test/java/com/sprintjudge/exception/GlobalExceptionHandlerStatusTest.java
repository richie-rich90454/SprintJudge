package com.sprintjudge.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("null")
class GlobalExceptionHandlerStatusTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void passthroughPreserves429() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
		org.springframework.http.ResponseEntity<com.sprintjudge.domain.dto.ErrorMessage> resp = handler
				.handleStatus(ex);
		assertEquals(429, resp.getStatusCode().value());
		assertEquals("ERROR", resp.getBody().getType());
		assertEquals("rate_limited", resp.getBody().getMessage());
	}

	@Test
	void passthroughPreserves400() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad-shape");
		org.springframework.http.ResponseEntity<com.sprintjudge.domain.dto.ErrorMessage> resp = handler
				.handleStatus(ex);
		assertEquals(400, resp.getStatusCode().value());
		assertEquals("bad-shape", resp.getBody().getMessage());
	}

	@Test
	void passthroughPreserves404() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "no-room");
		org.springframework.http.ResponseEntity<com.sprintjudge.domain.dto.ErrorMessage> resp = handler
				.handleStatus(ex);
		assertEquals(404, resp.getStatusCode().value());
		assertEquals("no-room", resp.getBody().getMessage());
	}

	@Test
	void nullReasonFallsBackToMessage() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);
		org.springframework.http.ResponseEntity<com.sprintjudge.domain.dto.ErrorMessage> resp = handler
				.handleStatus(ex);
		assertEquals(400, resp.getStatusCode().value());
		assertNotNull(resp.getBody().getMessage());
	}
}
