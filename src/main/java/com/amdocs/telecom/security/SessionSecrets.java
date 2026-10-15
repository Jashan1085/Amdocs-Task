package com.amdocs.telecom.security;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;

public final class SessionSecrets {
    public static final String PENDING = "telecom.pending";
    private static final String BINDING = "telecom.binding";
    private static final SecureRandom RANDOM = new SecureRandom();
    private SessionSecrets() { }

    public static String binding(HttpSession session) {
        synchronized (session) {
            String value = (String) session.getAttribute(BINDING);
            if (value == null) {
                byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
                value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(BINDING, value);
            }
            return value;
        }
    }
    public static PendingChallenge pending(HttpSession session) {
        return session == null ? null : (PendingChallenge) session.getAttribute(PENDING);
    }
    public static String hash(String value) { return SecurityHash.sha256(value); }
    public static boolean matches(String expected, String value) { return SecurityHash.matches(expected, value); }
}
