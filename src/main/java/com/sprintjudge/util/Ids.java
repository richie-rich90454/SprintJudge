package com.sprintjudge.util;

import java.security.SecureRandom;
import java.util.UUID;

public final class Ids {

    private static final SecureRandom RNG = new SecureRandom();

    private Ids() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String pin() {
        // Cryptographically unpredictable 6-digit PIN (was Math.random).
        int n = RNG.nextInt(900000) + 100000;
        return String.valueOf(n);
    }
}
