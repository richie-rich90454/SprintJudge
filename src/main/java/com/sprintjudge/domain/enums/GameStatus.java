package com.sprintjudge.domain.enums;

import java.util.Arrays;
import java.util.List;

public final class GameStatus {

	public static final String LOBBY = "LOBBY";

	public static final String ACTIVE = "ACTIVE";

	public static final String REVIEW = "REVIEW";

	public static final String ENDED = "ENDED";

	private static final List<String> VALUES = Arrays.asList(LOBBY, ACTIVE, REVIEW, ENDED);

	private GameStatus() {
	}

	public static String from(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Game status is required");
		}
		String normalized = value.trim().toUpperCase();
		if (!VALUES.contains(normalized)) {
			throw new IllegalArgumentException("No enum constant " + normalized);
		}
		return normalized;
	}
}
