package com.sprintjudge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window throttle on the form-login endpoint: brute-forcing the single
 * admin password must get progressively useless, not silently unlimited.
 * Uses the TCP peer address (what nginx actually connected from) — never
 * X-Forwarded-For, which is spoofable when the jar is exposed directly.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    static final int MAX_ATTEMPTS_PER_MIN = 10;
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_IPS = 10_000;

    final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/admin/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (windows.size() > MAX_TRACKED_IPS) windows.clear();
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        long[] window = windows.compute(ip, (k, v) -> {
            if (v == null || now - v[0] > WINDOW_MS) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (window[1] > MAX_ATTEMPTS_PER_MIN) {
            log.warn("Login throttled for IP: {}", ip);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("too many login attempts");
            return;
        }
        chain.doFilter(request, response);
    }
}
