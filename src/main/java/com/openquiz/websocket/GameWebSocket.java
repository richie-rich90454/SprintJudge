package com.openquiz.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.openquiz.domain.dto.ErrorMessage;
import com.openquiz.domain.dto.JoinedMessage;
import com.openquiz.domain.dto.RoomState;
import com.openquiz.service.GameRoomManager;
import com.openquiz.util.Json;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.standard.SpringConfigurator;

@Component
@ServerEndpoint(value = "/ws", configurator = SpringConfigurator.class)
public class GameWebSocket {

    private final GameRoomManager roomManager;

    private static final String UUID_KEY = "playerUuid";
    private static final String PIN_KEY = "pin";

    public GameWebSocket(GameRoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @OnOpen
    public void onOpen(Session session) {
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
                case "NEXT_QUESTION" -> roomManager.nextQuestion(pinOf(session));
                case "FORCE_SUBMIT" -> roomManager.forceSubmit(pinOf(session));
                case "END_GAME" -> roomManager.endGame(pinOf(session));
                case "EXTEND_TIMER" -> roomManager.extendTimer(pinOf(session), msg.path("seconds").asInt(30));
                case "KICK_PLAYER" -> roomManager.kickPlayer(pinOf(session), msg.path("playerUuid").asText());
                default -> send(session, new ErrorMessage("ERROR", "Unknown message type: " + type));
            }
        } catch (Exception e) {
            send(session, new ErrorMessage("ERROR", e.getMessage()));
        }
    }

    private void handleJoin(Session session, JsonNode msg) {
        // Schema validation: JOIN requires non-empty pin and name.
        String pin = msg.path("pin").asText("");
        String name = msg.path("name").asText("");
        if (pin.isBlank() || name.isBlank()) {
            send(session, new ErrorMessage("ERROR", "JOIN requires 'pin' and 'name'"));
            return;
        }
        try {
            var player = roomManager.join(pin, name, session.getId());
            session.getUserProperties().put(UUID_KEY, player.uuid());
            session.getUserProperties().put(PIN_KEY, pin);
            RoomState state = roomManager.getRoomState(pin);
            send(session, new JoinedMessage("JOINED", player.uuid(), state));
        } catch (IllegalArgumentException e) {
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
        String language = msg.path("language").asText("python");
        roomManager.submit(pin, questionId, uuid, language, msg.get("response"));
    }

    @OnClose
    public void onClose(Session session) {
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
