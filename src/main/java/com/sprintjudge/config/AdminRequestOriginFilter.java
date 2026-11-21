package com.sprintjudge.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Same-origin check for state-changing admin calls. The CSRF cookie handler is
 * deliberately plain (the SPA echoes the raw token), so the browser Origin is
 * validated as a second, independent line of defence.
 */
@Component
public class AdminRequestOriginFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!request.getRequestURI().startsWith("/api/admin/"))
			return true;
		String method = request.getMethod();
		return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Browsers send Origin on cross-site writes; Referer is the fallback when a
		// client omits Origin. Requests with neither are left to the CSRF cookie.
		String origin = request.getHeader("Origin");
		String referer = request.getHeader("Referer");
		String candidate = origin != null ? origin : referer;
		if (candidate != null && !com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed(candidate,
				request.getHeader("Host"))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "origin_not_allowed");
			return;
		}
		chain.doFilter(request, response);
	}
}
