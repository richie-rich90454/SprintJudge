package com.sprintjudge.controller;

import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.RunRequest;
import com.sprintjudge.service.executor.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicControllerRunTest {

    @Mock CodeExecutor executor;
    @Mock HttpServletRequest http;
    @InjectMocks PublicController controller;

    @SuppressWarnings("unchecked")
    private Map<String, long[]> runWindowMap() throws Exception {
        Field f = PublicController.class.getDeclaredField("runWindow");
        f.setAccessible(true);
        return (Map<String, long[]>) f.get(controller);
    }

    private RunRequest req(String lang, String src) {
        return new RunRequest(lang, src, "", 5);
    }

    @Test
    void normalExecutionReturnsRunResult() {
        when(http.getRemoteAddr()).thenReturn("1.2.3.4");
        when(executor.run(any())).thenReturn(new RunResult(true, "hello", "", "ok"));

        RunResult result = controller.run(req("python", "print('hello')"), http);

        assertTrue(result.ok());
        assertEquals("hello", result.output());
        assertEquals("ok", result.status());
        verify(executor).run(any());
    }

    @Test
    void rateLimitReturnsRateLimitedAfter30Requests() throws Exception {
        when(http.getRemoteAddr()).thenReturn("10.0.0.1");
        when(executor.run(any())).thenReturn(new RunResult(true, "ok", "", "ok"));

        for (int i = 0; i < 30; i++) {
            controller.run(req("python", "x"), http);
        }

        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.run(req("python", "x"), http));
        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertEquals(1, runWindowMap().size());
    }

    @Test
    void windowResetsAfter60Seconds() throws Exception {
        when(http.getRemoteAddr()).thenReturn("10.0.0.2");
        when(executor.run(any())).thenReturn(new RunResult(true, "ok", "", "ok"));

        for (int i = 0; i < 30; i++) {
            controller.run(req("python", "x"), http);
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.run(req("python", "x"), http));

        // Inject a window timestamp 61 seconds in the past to simulate expiry.
        runWindowMap().get("10.0.0.2")[0] = System.currentTimeMillis() - 61_000;

        RunResult afterReset = controller.run(req("python", "x"), http);
        assertTrue(afterReset.ok());
        assertEquals("ok", afterReset.status());
    }

    @Test
    void evictStaleRateLimitsRemovesStaleKeepsFresh() throws Exception {
        Map<String, long[]> windows = runWindowMap();
        windows.clear();
        long now = System.currentTimeMillis();
        windows.put("stale-ip", new long[]{now - 130_000, 5});
        windows.put("fresh-ip", new long[]{now, 3});

        controller.evictStaleRateLimits();

        assertFalse(windows.containsKey("stale-ip"));
        assertTrue(windows.containsKey("fresh-ip"));
    }

    @Test
    void evictStaleRateLimitsKeepsBoundaryEntry() throws Exception {
        Map<String, long[]> windows = runWindowMap();
        windows.clear();
        windows.put("edge-ip", new long[]{System.currentTimeMillis(), 1});

        controller.evictStaleRateLimits();

        assertTrue(windows.containsKey("edge-ip"));
    }

    private static final jakarta.validation.Validator VALIDATOR =
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

    private static jakarta.validation.Validator validator() {
        return VALIDATOR;
    }

    private static RunRequest vr(String lang, String src, String stdin, int timeout) {
        return new RunRequest(lang, src, stdin, timeout);
    }

    @Test
    void vrNullLanguageViolates() {
        assertFalse(validator().validate(vr(null, "x", "", 5)).isEmpty());
    }

    @Test
    void vrBlankLanguageViolates() {
        assertFalse(validator().validate(vr("   ", "x", "", 5)).isEmpty());
    }

    @Test
    void vrEmptyLanguageViolates() {
        assertFalse(validator().validate(vr("", "x", "", 5)).isEmpty());
    }

    @Test
    void vrKnownLanguageValid() {
        assertTrue(validator().validate(vr("python", "x", "", 5)).isEmpty());
    }

    @Test
    void vrNullSourceViolates() {
        assertFalse(validator().validate(vr("python", null, "", 5)).isEmpty());
    }

    @Test
    void vrBlankSourceViolates() {
        assertFalse(validator().validate(vr("python", "  ", "", 5)).isEmpty());
    }

    @Test
    void vrSource65536Valid() {
        assertTrue(validator().validate(vr("python", "x".repeat(65536), "", 5)).isEmpty());
    }

    @Test
    void vrSource65537Violates() {
        assertFalse(validator().validate(vr("python", "x".repeat(65537), "", 5)).isEmpty());
    }

    @Test
    void vrSourceEmptyStringValidSizeButBlank() {
        var violations = validator().validate(vr("python", "", "", 5));
        assertFalse(violations.isEmpty());
    }

    @Test
    void vrStdinNullValid() {
        assertTrue(validator().validate(vr("python", "x", null, 5)).isEmpty());
    }

    @Test
    void vrStdinEmptyValid() {
        assertTrue(validator().validate(vr("python", "x", "", 5)).isEmpty());
    }

    @Test
    void vrStdin10000Valid() {
        assertTrue(validator().validate(vr("python", "x", "s".repeat(10000), 5)).isEmpty());
    }

    @Test
    void vrStdin10001Violates() {
        assertFalse(validator().validate(vr("python", "x", "s".repeat(10001), 5)).isEmpty());
    }

    @Test
    void vrTimeoutZeroValid() {
        assertTrue(validator().validate(vr("python", "x", "", 0)).isEmpty());
    }

    @Test
    void vrTimeoutNegativeValid() {
        assertTrue(validator().validate(vr("python", "x", "", -1)).isEmpty());
    }

    @Test
    void vrTimeout30Valid() {
        assertTrue(validator().validate(vr("python", "x", "", 30)).isEmpty());
    }

    @Test
    void vrTimeout31Violates() {
        assertFalse(validator().validate(vr("python", "x", "", 31)).isEmpty());
    }

    @Test
    void vrTimeoutHugeViolates() {
        assertFalse(validator().validate(vr("python", "x", "", 3600)).isEmpty());
    }

    @Test
    void vrFullyValidRequestHasNoViolations() {
        assertTrue(validator().validate(vr("node", "console.log(1)", "in", 10)).isEmpty());
    }

    @Test
    void vrControllerPassesTimeoutZeroThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-1");
        when(executor.run(any())).thenReturn(new RunResult(true, "z", "", "ok"));
        RunResult r = controller.run(vr("python", "x", "", 0), http);
        assertEquals("ok", r.status());
        verify(executor).run(argThat(q -> q.timeoutSec() == 0));
    }

    @Test
    void vrControllerPassesTimeout31Through() {
        when(http.getRemoteAddr()).thenReturn("vr-2");
        when(executor.run(any())).thenReturn(new RunResult(true, "z", "", "ok"));
        RunResult r = controller.run(vr("python", "x", "", 31), http);
        assertEquals("ok", r.status());
        verify(executor).run(argThat(q -> q.timeoutSec() == 31));
    }

    @Test
    void vrControllerPassesNegativeTimeoutThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-3");
        when(executor.run(any())).thenReturn(new RunResult(false, "", "", "timeout"));
        RunResult r = controller.run(vr("python", "x", "", -5), http);
        assertEquals("timeout", r.status());
    }

    @Test
    void vrControllerPassesBlankLanguageThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-4");
        when(executor.run(any())).thenReturn(new RunResult(false, "", "", "unsupported_language"));
        RunResult r = controller.run(vr("  ", "x", "", 5), http);
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void vrControllerPassesNullLanguageThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-5");
        when(executor.run(any())).thenReturn(new RunResult(false, "", "", "unsupported_language"));
        RunResult r = controller.run(vr(null, "x", "", 5), http);
        assertEquals("unsupported_language", r.status());
    }

    @Test
    void vrControllerPassesOversizedStdinThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-6");
        when(executor.run(any())).thenReturn(new RunResult(true, "big", "", "ok"));
        RunResult r = controller.run(vr("python", "x", "s".repeat(10001), 5), http);
        assertEquals("ok", r.status());
        verify(executor).run(argThat(q -> q.stdin().length() == 10001));
    }

    @Test
    void vrControllerPassesUnicodeSourceThrough() {
        when(http.getRemoteAddr()).thenReturn("vr-7");
        when(executor.run(any())).thenReturn(new RunResult(true, "uni", "", "ok"));
        RunResult r = controller.run(vr("python", "print('h\u00e9llo')", "", 5), http);
        assertEquals("uni", r.output());
    }

    @Test
    void vrEachIpTrackedIndependently() throws Exception {
        Map<String, long[]> windows = runWindowMap();
        windows.clear();
        when(http.getRemoteAddr()).thenReturn("vr-8");
        when(executor.run(any())).thenReturn(new RunResult(true, "ok", "", "ok"));
        controller.run(vr("python", "x", "", 5), http);
        when(http.getRemoteAddr()).thenReturn("vr-9");
        controller.run(vr("python", "x", "", 5), http);
        assertEquals(2, windows.size());
        assertTrue(windows.containsKey("vr-8"));
        assertTrue(windows.containsKey("vr-9"));
    }

    @Test
    void vrEvictKeepsMultipleFreshIps() throws Exception {
        Map<String, long[]> windows = runWindowMap();
        windows.clear();
        long now = System.currentTimeMillis();
        windows.put("vr-a", new long[]{now, 1});
        windows.put("vr-b", new long[]{now, 2});
        controller.evictStaleRateLimits();
        assertTrue(windows.containsKey("vr-a"));
        assertTrue(windows.containsKey("vr-b"));
    }
}
