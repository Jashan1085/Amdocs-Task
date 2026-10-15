package com.amdocs.telecom.security;

import java.time.Instant;
import java.util.*;

public final class OtpRecord {
    private final UUID challengeId;
    private final long userId;
    private final OtpPurpose purpose;
    private final String bindingHash;
    private final String codeHash;
    private final long authVersion;
    private final int attempts;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant consumedAt;

    public OtpRecord(UUID challengeId, long userId, OtpPurpose purpose, String bindingHash, String codeHash, long authVersion, int attempts, Instant createdAt, Instant expiresAt, Instant consumedAt) {
        this.challengeId = challengeId;
        this.userId = userId;
        this.purpose = purpose;
        this.bindingHash = bindingHash;
        this.codeHash = codeHash;
        this.authVersion = authVersion;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
    }
    public UUID getChallengeId() { return challengeId; }
    public long getUserId() { return userId; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getBindingHash() { return bindingHash; }
    public String getCodeHash() { return codeHash; }
    public long getAuthVersion() { return authVersion; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
