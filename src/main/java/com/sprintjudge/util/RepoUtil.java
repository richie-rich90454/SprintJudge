package com.sprintjudge.util;

/**
 * SQLite's JDBC driver returns INTEGER columns as Integer whenever the value
 * fits in 32 bits, regardless of the declared jOOQ type. Every BIGINT read
 * therefore goes through this widening helper — a raw cast explodes at
 * runtime on exactly the values that matter most (timestamps before 2038).
 */
public final class RepoUtil {

    private RepoUtil() {}

    public static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    public static Long asLongBoxed(Object value) {
        return value == null ? null : asLong(value);
    }
}
