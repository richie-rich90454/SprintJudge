package com.sprintjudge.util;

import java.util.UUID;

public final class Ids {

    private Ids() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String pin() {
        int n = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(n);
    }
}
