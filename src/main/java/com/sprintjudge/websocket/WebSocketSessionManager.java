package com.sprintjudge.websocket;

import com.sprintjudge.util.Json;
import jakarta.websocket.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session registry and fan-out primitive.
 *
 * <p>Performance contract: {@link #broadcast(Collection, Object)} serializes the
 * payload EXACTLY ONCE and writes the pre-rendered string to every session —
 * the old behavior re-serialized per recipient, which dominated CPU at scale.
 * Individual session failures are isolated and never abort the fan-out.
 */
@Component
public class WebSocketSessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, Session session) {
        sessions.put(sessionId, session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public void send(String sessionId, Object message) {
        sendRaw(sessionId, Json.write(message));
    }

    public void sendRaw(String sessionId, String json) {
        Session s = sessions.get(sessionId);
        if (s == null || !s.isOpen()) return;
        try {
            s.getBasicRemote().sendText(json);
        } catch (IOException ignored) {
            // Broken pipe / race with close: drop this recipient, keep fanning out.
        }
    }

    public void broadcast(Collection<String> sessionIds, Object message) {
        broadcastRaw(sessionIds, Json.write(message));
    }

    public void broadcastRaw(Collection<String> sessionIds, String json) {
        for (String id : sessionIds) {
            sendRaw(id, json);
        }
    }

    public int size() {
        return sessions.size();
    }
}
