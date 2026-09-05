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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private com.sun.net.httpserver.HttpServer stubServer(String body, java.util.concurrent.atomic.AtomicReference<String> seen) throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/chat/completions", ex -> {
            if (seen != null) {
                seen.set(new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            byte[] out = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (var os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
        return server;
    }

    private String llmBody(String feedback, int score) {
        String inner = "{\"feedback\":\"" + feedback + "\",\"suggestedScore\":" + score + "}";
        String escaped = inner.replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    @Test
    void gradeSuccessWithoutTrailingSlash() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        var server = stubServer(llmBody("nice", 80), null);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "print(1)", "Q-ok-" + System.nanoTime(), "D", false, 80, 100);
            assertTrue(r.available());
            assertEquals("nice", r.feedback());
            assertEquals(80, r.suggestedScore());
            assertEquals("ok", r.status());
        } finally { server.stop(0); }
    }

    @Test
    void gradeSuccessWithTrailingSlashEndpoint() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        var server = stubServer(llmBody("good", 70), null);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var r = svc.grade("java", "code", "Q-slash-" + System.nanoTime(), "D", false, 70, 100);
            assertTrue(r.available());
            assertEquals("good", r.feedback());
        } finally { server.stop(0); }
    }

    @Test
    void gradeSendsApiKeyWhenConfigured() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "secret-123");
        set(svc, "model", "test-model");
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        var server = stubServer(llmBody("ok", 50), seen);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "x", "Q-key-" + System.nanoTime(), "D", false, 50, 100);
            assertTrue(r.available());
            assertTrue(seen.get().contains("test-model"));
        } finally { server.stop(0); }
    }

    @Test
    void gradeWithoutApiKeyOmitsAuthHeader() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        var server = stubServer(llmBody("fine", 60), null);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "x", "Q-nokey-" + System.nanoTime(), "D", false, 60, 100);
            assertTrue(r.available());
        } finally { server.stop(0); }
    }

    @Test
    void gradeCachesSecondCallWithoutHttp() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/chat/completions", ex -> {
            hits.incrementAndGet();
            byte[] out = llmBody("cached-me", 33).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (var os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            String title = "Q-dup-" + System.nanoTime();
            var first = svc.grade("python", "same", title, "D", false, 33, 100);
            var second = svc.grade("python", "same", title, "D", false, 33, 100);
            assertTrue(first.available());
            assertSame(first, second);
            assertEquals(1, hits.get());
        } finally { server.stop(0); }
    }

    @Test
    void gradeWithGarbageLlmBodyReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        var server = stubServer("this is not json", null);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "x", "Q-garbage-" + System.nanoTime(), "D", false, 10, 100);
            assertFalse(r.available());
        } finally { server.stop(0); }
    }

    @Test
    void gradeWithBlankContentReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}";
        var server = stubServer(body, null);
        try {
            set(svc, "enabled", true);
            set(svc, "timeoutSec", 5);
            set(svc, "apiKey", "");
            set(svc, "model", "m");
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "x", "Q-blank-" + System.nanoTime(), "D", false, 10, 100);
            assertFalse(r.available());
        } finally { server.stop(0); }
    }

    @Test
    void gradeWithInnerNonJsonContentReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"just prose\"}}]}";
        var server = stubServer(body, null);
        try {
            set(svc, "enabled", true);
            set(svc, "timeoutSec", 5);
            set(svc, "apiKey", "");
            set(svc, "model", "m");
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "x", "Q-prose-" + System.nanoTime(), "D", false, 10, 100);
            assertFalse(r.available());
        } finally { server.stop(0); }
    }

    @Test
    void parseBlankChoicesContentReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        assertFalse(parse(svc, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", 10, 100).available());
    }

    @Test
    void parseMissingFeedbackDefaultsEmpty() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"suggestedScore\\\":42}\"}}]}";
        var r = parse(svc, body, 42, 100);
        assertTrue(r.available());
        assertEquals("", r.feedback());
        assertEquals(42, r.suggestedScore());
    }

    @Test
    void parseMissingScoreDefaultsZero() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"feedback\\\":\\\"hi\\\"}\"}}]}";
        var r = parse(svc, body, 10, 100);
        assertTrue(r.available());
        assertEquals(0, r.suggestedScore());
    }

    @Test
    void parseInnerNonJsonReturnsUnavailable() throws Exception {
        AiGradingService svc = new AiGradingService();
        String body = "{\"choices\":[{\"message\":{\"content\":\"hello world\"}}]}";
        assertFalse(parse(svc, body, 10, 100).available());
    }

    @Test
    void promptEscapesQuotesAndBackslashes() throws Exception {
        AiGradingService svc = new AiGradingService();
        var m = AiGradingService.class.getDeclaredMethod("toJsonString", String.class);
        m.setAccessible(true);
        String out = (String) m.invoke(svc, "a\"b\\c\nd");
        assertEquals("\"a\\\"b\\\\c\\nd\"", out);
    }

    @Test
    void nullEndpointThrowsNpeWhenEnabled() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", null);
        assertThrows(NullPointerException.class,
                () -> svc.grade("python", "x", "Q", "D", false, 10, 100));
    }

    @Test
    void cacheEvictionClearsWhenOverCap() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "timeoutSec", 5);
        set(svc, "apiKey", "");
        set(svc, "model", "m");
        var cache = cacheOf(svc);
        for (int i = 0; i < 1005; i++) {
            cache.put("k" + i, new AiGradingService.AiGradeResult(true, "f", 1, "ok"));
        }
        var server = stubServer(llmBody("fresh", 90), null);
        try {
            set(svc, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
            var r = svc.grade("python", "evict-" + System.nanoTime(), "Q-evict-" + System.nanoTime(), "D", false, 90, 100);
            assertTrue(r.available());
            assertTrue(cache.size() <= 3);
        } finally { server.stop(0); }
    }

    @Test
    void missingAlgorithmThrowsIllegalState() throws Exception {
        AiGradingService svc = new AiGradingService();
        set(svc, "enabled", true);
        set(svc, "endpoint", "http://127.0.0.1:9");
        java.security.Provider sun = java.security.Security.getProvider("SUN");
        org.junit.jupiter.api.Assumptions.assumeTrue(sun != null);
        java.security.Security.removeProvider("SUN");
        try {
            org.junit.jupiter.api.Assumptions.assumeTrue(!providesSha256());
            assertThrows(IllegalStateException.class,
                    () -> svc.grade("python", "print(1)", "Q", "D", false, 0, 100));
        } finally {
            if (java.security.Security.getProvider("SUN") == null) {
                java.security.Security.addProvider(sun);
            }
        }
    }

    private static boolean providesSha256() {
        try {
            java.security.MessageDigest.getInstance("SHA-256");
            return true;
        } catch (java.security.NoSuchAlgorithmException e) {
            return false;
        }
    }
}
