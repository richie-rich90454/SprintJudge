package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-testable surface of {@link SecurityConfig}. The {@code securityFilterChain}
 * request-matcher matrix needs a Spring context and is covered by boot tests, not here.
 */
class SecurityConfigTest {

    private static SecurityConfig config(String user, String password) {
        SecurityConfig cfg = new SecurityConfig();
        ReflectionTestUtils.setField(cfg, "adminUsername", user);
        ReflectionTestUtils.setField(cfg, "adminPassword", password);
        return cfg;
    }

    @Test
    void passwordEncoderRoundTrips() {
        SecurityConfig cfg = config("admin", "secret");
        var encoder = cfg.passwordEncoder();
        assertNotNull(encoder);
        String hash = encoder.encode("secret");
        assertTrue(encoder.matches("secret", hash));
        assertTrue(!encoder.matches("wrong", hash));
    }

    @Test
    void userDetailsContainsAdmin() {
        var service = config("boss", "s3cret").userDetailsService();
        var user = service.loadUserByUsername("boss");
        assertNotNull(user);
        assertEquals("boss", user.getUsername());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void defaultPasswordStillBuildsUser() {
        var service = config("admin", "changeme").userDetailsService();
        assertNotNull(service.loadUserByUsername("admin"));
    }

    @Test
    void corsSingleOrigin() {
        var src = config("admin", "x").corsConfigurationSource("http://localhost:5173");
        assertNotNull(src);
        var cfg = src.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/quizzes"));
        assertNotNull(cfg);
        assertTrue(cfg.getAllowedOriginPatterns().contains("http://localhost:5173"));
        assertTrue(cfg.getAllowCredentials());
    }

    @Test
    void corsMultipleOriginsSplit() {
        var src = config("admin", "x").corsConfigurationSource("https://a.example,https://b.example");
        var cfg = src.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/"));
        assertNotNull(cfg);
        assertEquals(2, cfg.getAllowedOriginPatterns().size());
    }

    @Test
    void corsMethodsAndHeaders() {
        var src = config("admin", "x").corsConfigurationSource("http://localhost:5173");
        var cfg = src.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/"));
        assertNotNull(cfg);
        assertTrue(cfg.getAllowedMethods().containsAll(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
        assertTrue(cfg.getAllowedHeaders().contains("Authorization"));
        assertTrue(cfg.getAllowedHeaders().contains("X-XSRF-TOKEN"));
    }
}
