package com.sprintjudge.service;

import java.util.Objects;

public final class Player {
	private String uuid;
	private String name;
	private int score;
	private String sessionId;
	private boolean connected;
	private String token;

	public Player() {
	}

	public Player(String uuid, String name, int score, String sessionId, boolean connected, String token) {
		this.uuid = uuid;
		this.name = name;
		this.score = score;
		this.sessionId = sessionId;
		this.connected = connected;
		this.token = token;
	}

	public Player(String uuid, String name, int score, String sessionId, boolean connected) {
		this(uuid, name, score, sessionId, connected, null);
	}

	public Player withScore(int score) {
		return new Player(uuid, name, score, sessionId, connected, token);
	}

	public Player withSession(String sessionId) {
		return new Player(uuid, name, score, sessionId, true, token);
	}

	public Player disconnected() {
		return new Player(uuid, name, score, sessionId, false, token);
	}

	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public int getScore() {
		return score;
	}
	public void setScore(int score) {
		this.score = score;
	}

	public String getSessionId() {
		return sessionId;
	}
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public boolean isConnected() {
		return connected;
	}
	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Player))
			return false;
		Player other = (Player) o;
		return score == other.score && connected == other.connected && Objects.equals(uuid, other.uuid)
				&& Objects.equals(name, other.name) && Objects.equals(sessionId, other.sessionId)
				&& Objects.equals(token, other.token);
	}

	@Override
	public int hashCode() {
		return Objects.hash(uuid, name, score, sessionId, connected, token);
	}

	@Override
	public String toString() {
		return "Player[uuid=" + uuid + ", name=" + name + ", score=" + score + ", sessionId=" + sessionId
				+ ", connected=" + connected + ", token=" + token + "]";
	}
}
