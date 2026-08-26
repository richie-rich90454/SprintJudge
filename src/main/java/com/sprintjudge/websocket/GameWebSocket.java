package com.sprintjudge.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.sprintjudge.domain.dto.ErrorMessage;
import com.sprintjudge.domain.dto.JoinedMessage;
import com.sprintjudge.domain.dto.RoomState;
import com.sprintjudge.service.GameRoomManager;
import com.sprintjudge.util.Json;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ServerEndpoint(value = "/ws", configurator = SecureHandshakeConfigurator.class)
public class GameWebSocket {

    private final GameRoomManager roomManager;
    private final JoinRateLimiter rateLimiter;
    private final WebSocketSessionManager sessions;

    private static final String UUID_KEY = "playerUuid";
    private static final String PIN_KEY = "pin";
    private static final String ROLE_KEY = "role";
    private static final String AUTHED_KEY = SecureHandshakeConfigurator.AUTHENTICATED;
    private static final String IP_KEY = SecureHandshakeConfigurator.REMOTE_ADDR;

    /** Whitelist — also prevents script-path traversal via a crafted language value. */
    private static final Set<String> LANGUAGES = Set.of("c", "cpp", "java", "node", "python");
    private static final int MAX_SOURCE_CHARS = 65_536;

    public GameWebSocket(GameRoomManager roomManager, JoinRateLimiter rateLimiter,
                         WebSocketSessionManager sessions) {
        this.roomManager = roomManager;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        Object authed = config.getUserProperties().get(AUTHED_KEY);
        Object addr = config.getUserProperties().get(IP_KEY);
        session.getUserProperties().put(AUTHED_KEY, Boolean.TRUE.equals(authed));
        session.getUserProperties().put(IP_KEY, addr == null ? "unknown" : addr);
        // Without registration the fan-out map stays empty and every broadcast
        // (QUESTION_START, ROOM_STATE, LEADERBOARD_DELTA) is silently dropped.
        sessions.register(session.getId(), session);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        try {
            JsonNode msg = Json.readTree(message);
            // Schema validation: a well-formed message must declare a string type.
            if (!msg.isObject() || msg.path("type").isMissingNode() || msg.path("type").asText("").isBlank()) {
                send(session, new ErrorMessage("ERROR", "Malformed message: missing 'type'"));
                return;
            }
            String type = msg.path("type").asText("");
            switch (type) {
                case "JOIN" -> handleJoin(session, msg);
                case "SUBMIT" -> handleSubmit(session, msg);
                case "NEXT_QUESTION" -> asHost(session, () -> roomManager.nextQuestion(pinOf(session)));
                case "FORCE_SUBMIT" -> asHost(session, () -> roomManager.forceSubmit(pinOf(session)));
                case "END_GAME" -> asHost(session, () -> roomManager.endGame(pinOf(session)));
                case "EXTEND_TIMER" -> asHost(session, () ->
                        roomManager.extendTimer(pinOf(session), clampSeconds(msg.path("seconds").asInt(30))));
                case "KICK_PLAYER" -> asHost(session, () ->
                        roomManager.kickPlayer(pinOf(session), msg.path("playerUuid").asText()));
                case "RESYNC_LEADERBOARD" -> resyncLeaderboard(session);
                default -> send(session, new ErrorMessage("ERROR", "Unknown message type: " + type));
            }
        } catch (Exception e) {
            send(session, new ErrorMessage("ERROR", e.getMessage()));
        }
    }

    private void handleJoin(Session session, JsonNode msg) {
        String ip = (String) session.getUserProperties().get(IP_KEY);
        // Schema validation: JOIN requires non-empty pin and name.
        String pin = msg.path("pin").asText("");
        String name = msg.path("name").asText("");
        String role = msg.path("role").asText("player");
        if (pin.isBlank() || name.isBlank()) {
            send(session, new ErrorMessage("ERROR", "JOIN requires 'pin' and 'name'"));
            return;
        }
        boolean authenticated = Boolean.TRUE.equals(session.getUserProperties().get(AUTHED_KEY));
        if ("host".equalsIgnoreCase(role) && !authenticated) {
            send(session, new ErrorMessage("ERROR", "Host role requires admin authentication"));
            return;
        }
        if (!rateLimiter.tryJoin(ip)) {
            send(session, new ErrorMessage("ERROR", "Too many attempts. Try again shortly."));
            return;
        }
        try {
            var player = roomManager.join(pin, name, session.getId(), role);
            rateLimiter.recordSuccess(ip);
            session.getUserProperties().put(UUID_KEY, player.uuid());
            session.getUserProperties().put(PIN_KEY, pin);
            session.getUserProperties().put(ROLE_KEY, role.toLowerCase());
            RoomState state = roomManager.getRoomState(pin);
            send(session, new JoinedMessage("JOINED", player.uuid(), state));
        } catch (IllegalArgumentException | IllegalStateException e) {
            rateLimiter.recordFailure(ip);
            send(session, new ErrorMessage("ERROR", e.getMessage()));
        }
    }

    private void handleSubmit(Session session, JsonNode msg) {
        String uuid = (String) session.getUserProperties().get(UUID_KEY);
        String pin = pinOf(session);
        if (uuid == null || pin == null) {
            send(session, new ErrorMessage("ERROR", "Join a room first"));
            return;
        }
        // Schema validation: SUBMIT requires a questionId and a response payload.
        String questionId = msg.path("questionId").asText("");
        if (questionId.isBlank() || msg.path("response").isMissingNode()) {
            send(session, new ErrorMessage("ERROR", "SUBMIT requires 'questionId' and 'response'"));
            return;
        }
        String language = msg.path("language").asText("python").toLowerCase();
        if (!LANGUAGES.contains(language)) {
            send(session, new ErrorMessage("ERROR", "Unsupported language"));
            return;
        }
        JsonNode response = msg.get("response");
        if (response.hasNonNull("source") && response.get("source").asText().length() > MAX_SOURCE_CHARS) {
            send(session, new ErrorMessage("ERROR", "Source exceeds 64KB limit"));
            return;
        }
        roomManager.submit(pin, questionId, uuid, language, response);
    }

    /** Host-only guard: the connection must have joined as host AND be authenticated. */
    private void asHost(Session session, Runnable action) {
        String role = (String) session.getUserProperties().get(ROLE_KEY);
        boolean authenticated = Boolean.TRUE.equals(session.getUserProperties().get(AUTHED_KEY));
        if (!"host".equals(role) || !authenticated) {
            send(session, new ErrorMessage("ERROR", "Forbidden: host authentication required"));
            return;
        }
        action.run();
    }

    private int clampSeconds(int seconds) {
        return Math.max(1, Math.min(300, seconds));
    }

    /** Client detected a seq gap: push an authoritative full snapshot. */
    private void resyncLeaderboard(Session session) {
        String pin = pinOf(session);
        if (pin == null) {
            send(session, new ErrorMessage("ERROR", "Join a room first"));
            return;
        }
        roomManager.sendFullLeaderboard(pin, session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.unregister(session.getId());
        String pin = pinOf(session);
        String uuid = (String) session.getUserProperties().get(UUID_KEY);
        if (pin != null && uuid != null) roomManager.leave(pin, uuid);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // connection-level errors are non-fatal; ignore
    }

    private String pinOf(Session session) {
        return (String) session.getUserProperties().get(PIN_KEY);
    }

    private void send(Session session, Object message) {
        try {
            if (session.isOpen()) session.getBasicRemote().sendText(Json.write(message));
        } catch (Exception ignored) {
        }
    }
}
