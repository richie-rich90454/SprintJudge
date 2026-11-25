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

import java.util.List;
import java.util.Map;
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
	private static final String ADMIN_KEY = SecureHandshakeConfigurator.ADMIN;
	private static final String IP_KEY = SecureHandshakeConfigurator.REMOTE_ADDR;

	/**
	 * Whitelist - also prevents script-path traversal via a crafted language value.
	 */
	private static final Set<String> LANGUAGES = Set.of("c", "cpp", "java", "node", "python");
	private static final int MAX_SOURCE_CHARS = 65_536;

	public GameWebSocket(GameRoomManager roomManager, JoinRateLimiter rateLimiter, WebSocketSessionManager sessions) {
		this.roomManager = roomManager;
		this.rateLimiter = rateLimiter;
		this.sessions = sessions;
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		// Auth comes from the staged handshake (form-login session); the container
		// principal is a fallback for tests and non-standard containers.
		boolean authed;
		boolean admin = false;
		String addr;
		if (config instanceof jakarta.websocket.server.ServerEndpointConfig) {
			jakarta.websocket.server.ServerEndpointConfig sec = (jakarta.websocket.server.ServerEndpointConfig) config;
			SecureHandshakeConfigurator.StagedConnection staged = SecureHandshakeConfigurator.consumeStaged(sec);
			addr = staged.getIp();
			authed = staged.isAuthed() || session.getUserPrincipal() != null;
			admin = staged.isAdmin();
		} else {
			authed = session.getUserPrincipal() != null;
			addr = "unresolved";
		}
		session.getUserProperties().put(AUTHED_KEY, authed);
		session.getUserProperties().put(ADMIN_KEY, admin);
		session.getUserProperties().put(IP_KEY, addr);
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
			if ("JOIN".equals(type)) {
				handleJoin(session, msg);
			} else if ("SUBMIT".equals(type)) {
				handleSubmit(session, msg);
			} else if ("NEXT_QUESTION".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.nextQuestion(pinOf(session));
					}
				});
			} else if ("FORCE_SUBMIT".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.forceSubmit(pinOf(session));
					}
				});
			} else if ("END_GAME".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.endGame(pinOf(session));
					}
				});
			} else if ("EXTEND_TIMER".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.extendTimer(pinOf(session), clampSeconds(msg.path("seconds").asInt(30)));
					}
				});
			} else if ("KICK_PLAYER".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.kickPlayer(pinOf(session), msg.path("playerUuid").asText());
					}
				});
			} else if ("RESYNC_LEADERBOARD".equals(type)) {
				resyncLeaderboard(session);
			} else if ("PING".equals(type)) {
				send(session, new ErrorMessage("PONG", ""));
			} else if ("CREATE_TEAM".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						String name = msg.path("name").asText("Team");
						com.sprintjudge.service.GameRoom.Team team = roomManager.createTeam(pinOf(session), name);
						send(session, Map.of("type", "TEAM_CREATED", "teamId", team.getId(), "name", team.getName()));
					}
				});
			} else if ("JOIN_TEAM".equals(type)) {
				String teamId = msg.path("teamId").asText("");
				String uuid = (String) session.getUserProperties().get(UUID_KEY);
				if (uuid != null && !teamId.isBlank()) {
					try {
						com.sprintjudge.service.GameRoom.Team team = roomManager.joinTeam(pinOf(session), teamId, uuid);
						if (team != null)
							send(session, Map.of("type", "TEAM_JOINED", "teamId", team.getId()));
					} catch (IllegalArgumentException | IllegalStateException e) {
						send(session, new ErrorMessage("ERROR", e.getMessage()));
					}
				}
			} else if ("LEAVE_TEAM".equals(type)) {
				String uuid = (String) session.getUserProperties().get(UUID_KEY);
				if (uuid != null) {
					try {
						roomManager.leaveTeam(pinOf(session), uuid);
					} catch (IllegalArgumentException | IllegalStateException e) {
						send(session, new ErrorMessage("ERROR", e.getMessage()));
					}
				}
			} else if ("GET_TEAMS".equals(type)) {
				String pin = pinOf(session);
				if (pin == null) {
					send(session, new ErrorMessage("ERROR", "Join a room first"));
				} else {
					List<com.sprintjudge.service.GameRoom.Team> teams = roomManager.getTeams(pin);
					send(session, Map.of("type", "TEAM_LIST", "teams", teams));
				}
			} else if ("START_BATTLE".equals(type)) {
				asHost(session, new Runnable() {
					@Override
					public void run() {
						roomManager.startBattle(pinOf(session));
					}
				});
			} else if ("GET_BRACKET".equals(type)) {
				String pin = pinOf(session);
				if (pin == null) {
					send(session, new ErrorMessage("ERROR", "Join a room first"));
				} else {
					send(session, roomManager.getBracketView(pin));
				}
			} else {
				send(session, new ErrorMessage("ERROR", "Unknown message type: " + type));
			}
		} catch (Exception e) {
			// Never leak internal stack traces to clients; log server-side only.
			org.slf4j.LoggerFactory.getLogger(GameWebSocket.class).warn("WS message error", e);
			send(session, new ErrorMessage("ERROR", "Internal error"));
		}
	}

	private void handleJoin(Session session, JsonNode msg) {
		String ip = (String) session.getUserProperties().get(IP_KEY);
		String prevPin = (String) session.getUserProperties().get(PIN_KEY);
		String prevUuid = (String) session.getUserProperties().get(UUID_KEY);

		// Schema validation: JOIN requires non-empty pin and name.
		String pin = msg.path("pin").asText("");
		String name = msg.path("name").asText("");
		String role = msg.path("role").asText("player");
		if (pin.isBlank() || name.isBlank()) {
			send(session, new ErrorMessage("ERROR", "JOIN requires 'pin' and 'name'"));
			return;
		}
		if (!pin.matches("\\d{6}")) {
			// Reject before Integer.parseInt so JVM number-format internals
			// never reach unauthenticated clients.
			send(session, new ErrorMessage("ERROR", "Invalid PIN"));
			return;
		}
		boolean authenticated = isAuthenticated(session);
		boolean admin = isAdmin(session);
		if ("host".equalsIgnoreCase(role) && !(authenticated && admin)) {
			send(session, new ErrorMessage("ERROR", "Host role requires admin authentication"));
			return;
		}
		if (!rateLimiter.tryJoin(ip)) {
			send(session, new ErrorMessage("ERROR", "Too many attempts. Try again shortly."));
			return;
		}
		String rejoinToken = msg.path("rejoinToken").asText(null);
		try {
			com.sprintjudge.service.Player player = roomManager.join(pin, name, session.getId(), role, rejoinToken);
			// ponytail: only leave old room AFTER new join succeeds AND pin changed - //
			// the old code left before join, so a failed join destroyed
			// the player's seat in the previous room.
			if (prevPin != null && prevUuid != null && !prevPin.equals(pin)) {
				try {
					roomManager.leave(prevPin, prevUuid);
				} catch (RuntimeException e) {
					org.slf4j.LoggerFactory.getLogger(GameWebSocket.class).debug("leave old room failed", e);
				}
			}
			rateLimiter.recordSuccess(ip);
			session.getUserProperties().put(UUID_KEY, player.getUuid());
			session.getUserProperties().put(PIN_KEY, pin);
			session.getUserProperties().put(ROLE_KEY, role.toLowerCase());
			RoomState state = roomManager.getRoomState(pin);
			send(session, new JoinedMessage("JOINED", player.getUuid(), player.getToken(), state));
			// Late join into a live round (refresh, second device): resend the
			// current question or the newcomer waits on standby forever.
			roomManager.sendCurrentQuestion(pin, session.getId());
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
		if ("spectator".equals(session.getUserProperties().get(ROLE_KEY))) {
			send(session, new ErrorMessage("ERROR", "Spectators cannot submit answers"));
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
		try {
			roomManager.submit(pin, questionId, uuid, language, response);
		} catch (IllegalArgumentException e) {
			send(session, new ErrorMessage("ERROR", e.getMessage()));
		}
	}

	/**
	 * Host-only guard: the connection must have joined as host AND still be an
	 * authenticated admin. Handshake flags are a snapshot, so the live principal is
	 * re-read on every control message.
	 */
	private void asHost(Session session, Runnable action) {
		String role = (String) session.getUserProperties().get(ROLE_KEY);
		boolean authenticated = isAuthenticated(session);
		boolean admin = isAdmin(session);
		if (!"host".equals(role) || !authenticated || !admin) {
			send(session, new ErrorMessage("ERROR", "Forbidden: host authentication required"));
			return;
		}
		action.run();
	}

	private org.springframework.security.core.Authentication liveAuth(Session session) {
		try {
			java.security.Principal principal = session.getUserPrincipal();
			return principal instanceof org.springframework.security.core.Authentication
					? (org.springframework.security.core.Authentication) principal
					: null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private boolean isAuthenticated(Session session) {
		org.springframework.security.core.Authentication auth = liveAuth(session);
		if (auth != null)
			return auth.isAuthenticated()
					&& !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
		// shortcut: containers without a Spring principal keep the handshake
		// flags; upgrade to a live SecurityContext lookup if one is deployed.
		return Boolean.TRUE.equals(session.getUserProperties().get(AUTHED_KEY));
	}

	private boolean isAdmin(Session session) {
		org.springframework.security.core.Authentication auth = liveAuth(session);
		if (auth != null)
			return auth.isAuthenticated()
					&& auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		return Boolean.TRUE.equals(session.getUserProperties().get(ADMIN_KEY));
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
		// Abrupt drops (tab closed, network lost) can leave the session
		// facade throwing: cleanup must never die before roomManager.leave,
		// or the seat leaks and rejoins see a phantom occupant.
		String id = idOf(session);
		if (id != null) {
			try {
				sessions.unregister(id);
			} catch (RuntimeException e) {
				org.slf4j.LoggerFactory.getLogger(GameWebSocket.class).debug("WS unregister failed for {}", id, e);
			}
		}
		String pin = propOf(session, PIN_KEY);
		String uuid = propOf(session, UUID_KEY);
		if (pin != null && uuid != null) {
			try {
				roomManager.leave(pin, uuid, session.getId());
			} catch (RuntimeException e) {
				org.slf4j.LoggerFactory.getLogger(GameWebSocket.class).debug("WS leave failed for room {}", pin, e);
			}
		}
	}

	@OnError
	public void onError(Session session, Throwable error) {
		org.slf4j.LoggerFactory.getLogger(GameWebSocket.class).warn("WebSocket error on session {}", idOf(session),
				error);
		// Some containers don't call onClose after onError - ensure cleanup.
		onClose(session);
	}

	private static String idOf(Session session) {
		try {
			return session.getId();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String propOf(Session session, String key) {
		try {
			Object value = session.getUserProperties().get(key);
			if (value instanceof String) {
				return (String) value;
			}
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private String pinOf(Session session) {
		return (String) session.getUserProperties().get(PIN_KEY);
	}

	private void send(Session session, Object message) {
		try {
			sessions.sendRaw(session.getId(), Json.write(message));
		} catch (Exception ignored) {
		}
	}
}
