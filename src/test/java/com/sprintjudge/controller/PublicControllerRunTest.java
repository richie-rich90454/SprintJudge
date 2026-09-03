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
}
