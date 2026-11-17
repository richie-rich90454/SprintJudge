package com.sprintjudge.domain.enums;

import org.eclipse.jdt.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public final class Role {

	public static final String ADMIN = "ADMIN";

	public static final String PLAYER = "PLAYER";

	private static final List<String> VALUES = Arrays.asList(ADMIN, PLAYER);

	private Role() {
	}

	public static String from(@Nullable String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Role is required");
		}
		String normalized = value.trim().toUpperCase();
		if (!VALUES.contains(normalized)) {
			throw new IllegalArgumentException("No enum constant " + normalized);
		}
		return normalized;
	}
}
