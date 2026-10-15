package com.amdocs.telecom.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class SecurityHash {
    private SecurityHash() { }
    static String sha256(String value) {
        try {
            StringBuilder result = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    static boolean matches(String expectedHash, String value) {
        return value != null && MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                sha256(value).getBytes(StandardCharsets.US_ASCII));
    }
}
