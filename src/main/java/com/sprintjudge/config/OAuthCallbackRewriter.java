package com.sprintjudge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Rewrites custom OAuth2 callback paths to Spring Security's expected path.
 *
 * <p>The user registers /api/oauth2/mscallback in Azure Portal; this filter
 * transparently translates it to /login/oauth2/code/microsoft so Spring
 * Security's OAuth2LoginAuthenticationFilter can process it. This avoids any
 * dependency on Spring Security internals while honoring arbitrary callback
 * paths chosen by the deployer.
 *
 * <p>Runs at HIGHEST_PRECEDENCE so it executes BEFORE the security chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthCallbackRewriter extends OncePerRequestFilter {

    /** Maps external callback paths → Spring Security's expected path. */
    private static final Map<String, String> CALLBACK_MAP = Map.of(
            "/api/oauth2/mscallback", "/login/oauth2/code/microsoft",
            "/api/oauth2/dccallback", "/login/oauth2/code/discord"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = req.getRequestURI();
        String mapped = CALLBACK_MAP.get(uri);
        if (mapped != null) {
            final String rewritten = mapped;
            HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
                @Override public String getRequestURI() { return rewritten; }
                @Override public StringBuffer getRequestURL() {
                    var url = new StringBuffer(super.getRequestURL());
                    int idx = url.indexOf(uri);
                    if (idx >= 0) url.replace(idx, idx + uri.length(), rewritten);
                    return url;
                }
                @Override public String getServletPath() { return rewritten; }
            };
            chain.doFilter(wrapped, res);
            return;
        }
        chain.doFilter(req, res);
    }
}
