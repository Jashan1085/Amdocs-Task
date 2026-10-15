package com.amdocs.telecom.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** An opaque session handle, never a password or a usable authentication token. */
public final class PendingChallenge implements Serializable {
    private static final long serialVersionUID = 1L;
    private final UUID id;
    private final OtpPurpose purpose;
    private final Instant expiresAt;

    public PendingChallenge(UUID id, OtpPurpose purpose, Instant expiresAt) {
        this.id = id;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }
    public UUID getId() { return id; }
    public OtpPurpose getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
}
