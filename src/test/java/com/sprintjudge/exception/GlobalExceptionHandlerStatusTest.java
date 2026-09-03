package com.sprintjudge.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void passthroughPreserves429() {
        var ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
        var resp = handler.handleStatus(ex);
        assertEquals(429, resp.getStatusCode().value());
        assertEquals("ERROR", resp.getBody().type());
        assertEquals("rate_limited", resp.getBody().message());
    }

    @Test
    void passthroughPreserves400() {
        var ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad-shape");
        var resp = handler.handleStatus(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("bad-shape", resp.getBody().message());
    }

    @Test
    void passthroughPreserves404() {
        var ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "no-room");
        var resp = handler.handleStatus(ex);
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("no-room", resp.getBody().message());
    }

    @Test
    void nullReasonFallsBackToMessage() {
        var ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var resp = handler.handleStatus(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody().message());
    }
}
