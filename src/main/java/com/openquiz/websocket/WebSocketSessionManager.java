package com.openquiz.websocket;

import com.openquiz.util.Json;
import jakarta.websocket.Session;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        Session s = sessions.get(sessionId);
        if (s == null || !s.isOpen()) return;
        try {
            s.getBasicRemote().sendText(Json.write(message));
        } catch (IOException ignored) {
        }
    }

    public void broadcast(Collection<String> sessionIds, Object message) {
        String text = Json.write(message);
        for (String id : sessionIds) {
            Session s = sessions.get(id);
            if (s != null && s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(text);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
