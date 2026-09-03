package com.sprintjudge.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Jakarta WebSocket endpoints to the Spring context WITHOUT requiring
 * a root ContextLoaderListener (Spring Boot never registers one).
 *
 * <ul>
 *   <li>{@link #getEndpointInstance} resolves the endpoint as a Spring bean,
 *       so constructor injection works.</li>
 *   <li>{@link #modifyHandshake} captures per-connection security context:
 *       whether the upgrading request carried an authenticated principal and
 *       the client address (proxy headers first) used for rate limiting.</li>
 * </ul>
 *
 * The container builds its own configurator instance per deployment; the
 * static context reference is populated when Spring constructs THIS bean.
 */
@Component
public class SecureHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    public static final String AUTHENTICATED = "oq.authenticated";
    public static final String REMOTE_ADDR = "oq.remoteAddr";

    private static volatile ApplicationContext context;

    /**
     * Per-connection staging maps. The handshake writes IP + auth here keyed by a
     * unique token; onOpen reads and removes them. This avoids the race where two
     * concurrent handshakes overwrite each other on the shared ServerEndpointConfig.
     */
    private static final ConcurrentHashMap<String, String> stagedIPs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> stagedAuth = new ConcurrentHashMap<>();

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
        String addr = firstHeader(request, "X-Forwarded-For");
        if (addr == null) addr = firstHeader(request, "X-Real-IP");
        String remoteAddr = addr == null ? "unresolved" : addr;
        // Stage IP + auth for this connection — onOpen reads-and-removes them.
        // The WS upgrade carries the JSESSIONID cookie, so the HttpSession holds
        // the form-login auth even though /ws itself is permitAll.
        String token = java.util.UUID.randomUUID().toString();
        stagedIPs.put(token, remoteAddr);
        stagedAuth.put(token, isAuthenticated(request));
        sec.getUserProperties().put(REMOTE_ADDR, token);
    }

    public record StagedConnection(String ip, boolean authed) {}

    /**
     * Called from onOpen to retrieve the staged IP + auth for this connection.
     * Removes both staging entries (no leak).
     */
    public static StagedConnection consumeStaged(ServerEndpointConfig config) {
        Object token = config.getUserProperties().get(REMOTE_ADDR);
        if (token == null) return new StagedConnection("unresolved", false);
        String ip = stagedIPs.remove(token);
        boolean authed = Boolean.TRUE.equals(stagedAuth.remove(token));
        return new StagedConnection(ip != null ? ip : "unresolved", authed);
    }

    /**
     * Called from onOpen to retrieve the staged IP for this connection.
     * Returns the IP address and removes the staging entry (no leak).
     */
    public static String consumeStagedIP(ServerEndpointConfig config) {
        return consumeStaged(config).ip();
    }

    private boolean isAuthenticated(HandshakeRequest request) {
        if (request.getUserPrincipal() != null) return true;
        try {
            Object httpSession = request.getHttpSession();
            if (httpSession instanceof jakarta.servlet.http.HttpSession session) {
                Object ctx = session.getAttribute("SPRING_SECURITY_CONTEXT");
                if (ctx instanceof org.springframework.security.core.context.SecurityContext security) {
                    var auth = security.getAuthentication();
                    return auth != null && auth.isAuthenticated()
                            && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String firstHeader(HandshakeRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        int comma = v.indexOf(',');
        return (comma > -1 ? v.substring(0, comma) : v).trim();
    }
}
