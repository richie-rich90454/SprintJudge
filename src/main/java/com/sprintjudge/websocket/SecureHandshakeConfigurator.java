package com.sprintjudge.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

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
        // Per-request security context is stashed on the SHARED config object; a
        // concurrent handshake could overwrite it before onOpen reads it, letting
        // an anonymous client inherit another's authenticated flag. We therefore
        // do NOT rely on these properties at all — onOpen reads the authoritative
        // per-session Principal directly from the live Session. Only the rate-
        // limiting source address is derived here (bucket attribution only).
        String addr = firstHeader(request, "X-Forwarded-For");
        if (addr == null) addr = firstHeader(request, "X-Real-IP");
        sec.getUserProperties().put(REMOTE_ADDR, addr == null ? "unresolved" : addr);
    }

    private String firstHeader(HandshakeRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        int comma = v.indexOf(',');
        return (comma > -1 ? v.substring(0, comma) : v).trim();
    }
}
