package com.sprintjudge.util;

import org.springframework.util.StringUtils;

/**
 * Edge case Z: player names may only contain alphanumerics, spaces, hyphens and
 * underscores. They are truncated to 20 characters and rejected (empty result)
 * if nothing valid remains. The whitelist already excludes every HTML
 * metacharacter, so no escaping pass is needed (and escaping would corrupt
 * legitimate non-ASCII letters such as é).
 */
public final class NameSanitizer {

    public static final int MAX_LENGTH = 20;

    private NameSanitizer() {}

    public static String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        StringBuilder out = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                out.append(c);
            }
            if (out.length() >= MAX_LENGTH) break;
        }
        String cleaned = out.toString().trim();
        return cleaned;
    }
}
