package com.amdocs.telecom.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() { }
    public static boolean accepts(String password) {
        return password != null && password.length() >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
