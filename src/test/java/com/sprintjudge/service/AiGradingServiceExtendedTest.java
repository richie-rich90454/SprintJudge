package com.sprintjudge.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGradingServiceExtendedTest {

    private void set(Object target, String name, Object value) throws Exception {
        Field f = AiGradingService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, AiGradingService.AiGradeResult> cacheOf(AiGradingService svc) throws Exception {
        Field f = AiGradingService.class.getDeclaredField("cache");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, AiGradingService.AiGradeResult>) f.get(svc);
    }

    private String cacheKey(String language, String source, String title) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest((language + "||" + source + "||" + title).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private AiGradingService.AiGradeResult parse(AiGradingService svc, String body, int score, int max) throws Exception {
        Method m = AiGradingService.class.getDeclaredMethod("parseResponse", String.class, int.class, int.class);
        m.setAccessible(true);
        return (AiGradingService.AiGradeResult) m.invoke(svc, body, score, max);
    }

    @Test
    void defaultsAreDisabled() {
        AiGradingService svc = new AiGradingService();
        assertFalse(svc.isEnabled());
        assertNull(svc.getProvider());
    }

    @Test
    void disabledReturnsUnavailableWithoutTouchingNetwork() {
        AiGradingService svc = new AiGradingService();
        var r = svc.grade("python", "print(1)", "Q", "D", true, 100, 100);
        assertFalse(r.available());
        assertEquals("unavailable", r.status());
        assertEquals(0, r.suggestedScore());
    }

    @Test
    void blankEndpointReturnsUnavailableWhenEnabled() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "   ");
        var r = svc.grade("python", "print(1)", "Q", "D", true, 100, 100);
        assertFalse(r.available());
        assertEquals("unavailable", r.status());
    }

    @Test
    void emptyEndpointReturnsUnavailableWhenEnabled() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "");
        var r = svc.grade("python", "print(1)", "Q", "D", true, 100, 100);
        assertFalse(r.available());
    }

    @Test
    void providerRoundTripsThroughReflection() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "provider", "llamacpp");
        assertEquals("llamacpp", svc.getProvider());
    }

    @Test
    void cacheHitReturnsSameInstanceWithoutHttp() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "http://127.0.0.1:1");
        set(svc, "timeoutSec", 2);
        var cached = new AiGradingService.AiGradeResult(true, "cached", 42, "ok");
        cacheOf(svc).put(cacheKey("python", "print(1)", "Q"), cached);
        var r = svc.grade("python", "print(1)", "Q", "D", true, 100, 100);
        assertSame(cached, r);
    }

    @Test
    void cacheMissAgainstUnreachableEndpointReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "http://127.0.0.1:1");
        set(svc, "timeoutSec", 1);
        var r = svc.grade("python", "print(1)", "Q-miss-" + System.nanoTime(), "D", true, 100, 100);
        assertFalse(r.available());
        assertEquals("unavailable", r.status());
    }

    @Test
    void parseMalformedJsonReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        var r = parse(svc, "not-json{{{", 100, 100);
        assertFalse(r.available());
    }

    @Test
    void parseMissingChoicesReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        assertFalse(parse(svc, "{}", 100, 100).available());
        assertFalse(parse(svc, "{\"choices\":[]}", 100, 100).available());
    }

    @Test
    void parseValidResponseClampsScoreToMax() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"feedback\\\":\\\"great\\\",\\\"suggestedScore\\\":999}\"}}]}";
        var r = parse(svc, body, 100, 100);
        assertTrue(r.available());
        assertEquals("ok", r.status());
        assertEquals("great", r.feedback());
        assertEquals(100, r.suggestedScore());
    }

    @Test
    void parseNegativeScoreClampsToZero() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"feedback\\\":\\\"bad\\\",\\\"suggestedScore\\\":-5}\"}}]}";
        var r = parse(svc, body, 0, 100);
        assertTrue(r.available());
        assertEquals(0, r.suggestedScore());
    }

    @Test
    void failedGradesAreNotCached() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "http://127.0.0.1:1");
        set(svc, "timeoutSec", 1);
        String title = "Q-cache-" + System.nanoTime();
        var r = svc.grade("python", "x=1", title, "D", true, 10, 100);
        assertFalse(r.available());
        assertTrue(cacheOf(svc).isEmpty());
    }
}
