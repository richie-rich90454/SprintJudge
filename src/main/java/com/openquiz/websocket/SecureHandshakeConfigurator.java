package com.openquiz.websocket;

import org.springframework.web.socket.server.standard.SpringConfigurator;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Captures per-connection security context during the WebSocket upgrade:
 * whether the upgrading HTTP request carried an authenticated principal
 * (Microsoft OAuth2 session) and the remote address (for rate limiting).
 * Vanilla Jakarta WebSocket has no Spring Security filter chain on messages,
 * so the upgrade request is the one trusted moment to bind identity.
 */
public class SecureHandshakeConfigurator extends SpringConfigurator {

    public static final String AUTHENTICATED = "oq.authenticated";
    public static final String REMOTE_ADDR = "oq.remoteAddr";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        super.modifyHandshake(sec, request, response);
        sec.getUserProperties().put(AUTHENTICATED, request.getUserPrincipal() != null);
        // Jakarta's handshake API exposes no remote address; derive it from proxy
        // headers (nginx sets X-Forwarded-For / X-Real-IP) with a shared fallback
        // bucket so unresolved clients still hit the limiter.
        String addr = firstHeader(request, "X-Forwarded-For");
        if (addr == null) addr = firstHeader(request, "X-Real-IP");
        sec.getUserProperties().put(REMOTE_ADDR, addr == null ? "unresolved" : addr);
    }

    private String firstHeader(HandshakeRequest request, String name) {
        java.util.List<String> values = request.getHeaders().get(name);
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        int comma = v.indexOf(',');
        return (comma > -1 ? v.substring(0, comma) : v).trim();
    }
}
