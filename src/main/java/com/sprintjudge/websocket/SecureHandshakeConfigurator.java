package com.sprintjudge.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Jakarta WebSocket endpoints to the Spring context WITHOUT requiring a
 * root ContextLoaderListener (Spring Boot never registers one).
 *
 * <ul>
 * <li>{@link #getEndpointInstance} resolves the endpoint as a Spring bean, so
 * constructor injection works.</li>
 * <li>{@link #modifyHandshake} captures per-connection security context:
 * whether the upgrading request carried an authenticated principal and the
 * client address (proxy headers first) used for rate limiting.</li>
 * </ul>
 *
 * The container builds its own configurator instance per deployment; the static
 * context reference is populated when Spring constructs THIS bean.
 */
@Component
public class SecureHandshakeConfigurator extends ServerEndpointConfig.Configurator {

	public static final String AUTHENTICATED = "oq.authenticated";
	public static final String REMOTE_ADDR = "oq.remoteAddr";

	private static volatile ApplicationContext context;

	/**
	 * Optional explicit origin allowlist (comma-separated); empty means same-host.
	 */
	static String allowedOrigins() {
		ApplicationContext ctx = context;
		if (ctx == null)
			return "";
		return ctx.getEnvironment().getProperty("SPRINTJUDGE_CORS_ALLOWED_ORIGINS", "");
	}

	public static boolean originAllowed(String origin, String host) {
		if (host == null || host.isBlank())
			return false;
		try {
			java.net.URI originUri = java.net.URI.create(origin);
			String originHost = originUri.getHost();
			if (originHost == null)
				return false;
			String requestedHost = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
			if (originHost.equalsIgnoreCase(requestedHost) || "localhost".equalsIgnoreCase(originHost)
					|| "127.0.0.1".equals(originHost)) {
				return true;
			}
			for (String allowed : allowedOrigins().split(",")) {
				String trimmed = allowed.trim();
				if (!trimmed.isEmpty() && trimmed.equalsIgnoreCase(origin))
					return true;
			}
			return false;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Per-connection staging maps. The handshake writes IP + auth here keyed by a
	 * unique token; onOpen reads and removes them. Entries are timestamped and
	 * opportunistically evicted: a handshake that never reaches onOpen must not
	 * leak memory to unauthenticated clients.
	 */
	private static final class Staged {
		private String ip;
		private boolean authed;
		private long nanos;

		public Staged(String ip, boolean authed, long nanos) {
			this.ip = ip;
			this.authed = authed;
			this.nanos = nanos;
		}

		public String getIp() {
			return ip;
		}

		public boolean isAuthed() {
			return authed;
		}

		public long getNanos() {
			return nanos;
		}
	}
	private static final ConcurrentHashMap<String, Staged> staged = new ConcurrentHashMap<>();
	private static final long STAGE_TTL_NANOS = TimeUnit.MINUTES.toNanos(5);
	private static final int STAGE_MAX = 10_000;

	@Autowired
	void holdApplicationContext(ApplicationContext applicationContext) {
		context = applicationContext;
	}

	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) {
		ApplicationContext c = context;
		if (c == null) {
			throw new IllegalStateException("Application context not yet available for WS endpoints");
		}
		return c.getBean(endpointClass);
	}

	@Override
	public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
		super.modifyHandshake(sec, request, response);
		// Proxy headers count only for a loopback proxy (nginx on this host).
		// Direct-to-jar deployments use the TCP peer, so a client cannot rotate
		// identities past the join rate limiter with fabricated headers.
		// WebSocketOriginFilter rejects fabricated proxy headers before upgrade,
		// so the last XFF segment is safe to use here.
		String forwarded = lastHeader(request, "X-Forwarded-For");
		String realIp = firstHeader(request, "X-Real-IP");
		String remoteAddr = forwarded != null ? forwarded : (realIp != null ? realIp : "unresolved");
		// Cross-site WebSocket hijacking: browsers always send Origin. Reject a
		// handshake whose Origin host differs from the requested Host unless an
		// explicit allowlist is configured.
		String origin = firstHeader(request, "Origin");
		String host = firstHeader(request, "Host");
		if (origin != null && !originAllowed(origin, host)) {
			sec.getUserProperties().put(REMOTE_ADDR, "blocked");
			return;
		}
		// Stage IP + auth for this connection - onOpen reads-and-removes them.
		// The WS upgrade carries the JSESSIONID cookie, so the HttpSession holds
		// the form-login auth even though /ws itself is permitAll.
		String token = java.util.UUID.randomUUID().toString();
		if (staged.size() >= STAGE_MAX)
			evictStale();
		staged.put(token, new Staged(remoteAddr, isAuthenticated(request), System.nanoTime()));
		sec.getUserProperties().put(REMOTE_ADDR, token);
	}

	public static final class StagedConnection {
		private String ip;
		private boolean authed;

		@SuppressWarnings("all")
		public StagedConnection() {
		}

		public StagedConnection(String ip, boolean authed) {
			this.ip = ip;
			this.authed = authed;
		}

		public String getIp() {
			return ip;
		}

		public void setIp(String ip) {
			this.ip = ip;
		}

		public boolean isAuthed() {
			return authed;
		}

		public void setAuthed(boolean authed) {
			this.authed = authed;
		}
	}

	/**
	 * Called from onOpen to retrieve the staged IP + auth for this connection.
	 * Removes the staging entry (no leak).
	 */
	public static StagedConnection consumeStaged(ServerEndpointConfig config) {
		Object token = config.getUserProperties().get(REMOTE_ADDR);
		if (token == null)
			return new StagedConnection("unresolved", false);
		Staged s = staged.remove(token.toString());
		if (s == null)
			return new StagedConnection("unresolved", false);
		return new StagedConnection(s.getIp() != null ? s.getIp() : "unresolved", s.isAuthed());
	}

	/**
	 * Called from onOpen to retrieve the staged IP for this connection. Returns the
	 * IP address and removes the staging entry (no leak).
	 */
	public static String consumeStagedIP(ServerEndpointConfig config) {
		return consumeStaged(config).getIp();
	}

	private boolean isAuthenticated(HandshakeRequest request) {
		if (request.getUserPrincipal() != null)
			return true;
		try {
			Object httpSession = request.getHttpSession();
			if (httpSession instanceof jakarta.servlet.http.HttpSession) {
				jakarta.servlet.http.HttpSession session = (jakarta.servlet.http.HttpSession) httpSession;
				Object ctx = session.getAttribute("SPRING_SECURITY_CONTEXT");
				if (ctx instanceof org.springframework.security.core.context.SecurityContext) {
					org.springframework.security.core.context.SecurityContext security = (org.springframework.security.core.context.SecurityContext) ctx;
					org.springframework.security.core.Authentication auth = security.getAuthentication();
					return auth != null && auth.isAuthenticated()
							&& !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static void evictStale() {
		long now = System.nanoTime();
		java.util.Iterator<Map.Entry<String, Staged>> it = staged.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Staged> e = it.next();
			if (now - e.getValue().getNanos() > STAGE_TTL_NANOS)
				it.remove();
		}
	}

	private String lastHeader(HandshakeRequest request, String name) {
		List<String> values = request.getHeaders().get(name);
		if (values == null || values.isEmpty())
			return null;
		String v = values.get(values.size() - 1);
		int comma = v.lastIndexOf(',');
		return (comma > -1 ? v.substring(comma + 1) : v).trim();
	}

	private String firstHeader(HandshakeRequest request, String name) {
		List<String> values = request.getHeaders().get(name);
		if (values == null || values.isEmpty())
			return null;
		String v = values.get(0);
		int comma = v.indexOf(',');
		return (comma > -1 ? v.substring(0, comma) : v).trim();
	}
}
