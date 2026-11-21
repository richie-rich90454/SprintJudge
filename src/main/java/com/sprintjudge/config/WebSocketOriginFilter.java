package com.sprintjudge.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects cross-origin WebSocket handshakes before the servlet container
 * upgrades the connection (CSWSH). Same-host and loopback origins are allowed
 * so the Vite dev proxy keeps working.
 */
@Component
public class WebSocketOriginFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"/ws".equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String origin = request.getHeader("Origin");
		if (origin != null && !com.sprintjudge.websocket.SecureHandshakeConfigurator.originAllowed(origin,
				request.getHeader("Host"))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "origin_not_allowed");
			return;
		}
		// Only a loopback proxy may inject client-IP headers. Reject direct
		// requests carrying them so the endpoint configurator can trust XFF.
		boolean proxyHeaders = request.getHeader("X-Forwarded-For") != null || request.getHeader("X-Real-IP") != null;
		if (proxyHeaders && !com.sprintjudge.util.ClientIpResolver.isLoopback(request.getRemoteAddr())) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "proxy_header_not_allowed");
			return;
		}
		chain.doFilter(request, response);
	}
}
