package com.sprintjudge.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginRateLimitFilterTest {

    private MockHttpServletRequest login(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/admin/login");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void skipsNonLoginPaths() {
        LoginRateLimitFilter f = new LoginRateLimitFilter();
        assertTrue(f.shouldNotFilter(new MockHttpServletRequest("GET", "/admin/login")));
        assertTrue(f.shouldNotFilter(new MockHttpServletRequest("POST", "/api/admin/games")));
        assertFalse(f.shouldNotFilter(login("1.2.3.4")));
    }

    @Test
    void allowsAttemptsUnderLimit() throws Exception {
        LoginRateLimitFilter f = new LoginRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < LoginRateLimitFilter.MAX_ATTEMPTS_PER_MIN; i++) {
            f.doFilterInternal(login("9.9.9.9"), new MockHttpServletResponse(), chain);
        }
        verify(chain, org.mockito.Mockito.times(LoginRateLimitFilter.MAX_ATTEMPTS_PER_MIN))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void throttlesPastLimit() throws Exception {
        LoginRateLimitFilter f = new LoginRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse last = new MockHttpServletResponse();
        for (int i = 0; i <= LoginRateLimitFilter.MAX_ATTEMPTS_PER_MIN; i++) {
            last = new MockHttpServletResponse();
            f.doFilterInternal(login("8.8.8.8"), last, chain);
        }
        assertEquals(429, last.getStatus());
        assertEquals("60", last.getHeader("Retry-After"));
        verify(chain, org.mockito.Mockito.times(LoginRateLimitFilter.MAX_ATTEMPTS_PER_MIN))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void limitsArePerIp() throws Exception {
        LoginRateLimitFilter f = new LoginRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i <= LoginRateLimitFilter.MAX_ATTEMPTS_PER_MIN; i++) {
            f.doFilterInternal(login("7.7.7.7"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse other = new MockHttpServletResponse();
        f.doFilterInternal(login("7.7.7.8"), other, chain);
        assertEquals(200, other.getStatus());
    }

    @Test
    void overflowClearsTracking() throws Exception {
        LoginRateLimitFilter f = new LoginRateLimitFilter();
        for (int i = 0; i < 10_001; i++) f.windows.put("10.0.0." + i, new long[]{0, 1});
        FilterChain chain = mock(FilterChain.class);
        f.doFilterInternal(login("10.9.9.9"), new MockHttpServletResponse(), chain);
        assertTrue(f.windows.size() < 10_001);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
