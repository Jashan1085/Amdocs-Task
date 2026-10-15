package com.amdocs.telecom.security;

import java.time.Instant;
import java.util.*;

public final class AuthAccount {
    private final long userId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final int failedAttempts;
    private final Instant lockedUntil;
    private final long authVersion;
    private final Long customerId;
    private final Long engineerId;
    private final List<Role> roles;

    public AuthAccount(long userId, String username, String displayName, String passwordHash, boolean enabled, int failedAttempts, Instant lockedUntil, long authVersion, Long customerId, Long engineerId, List<Role> roles) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.authVersion = authVersion;
        this.customerId = customerId;
        this.engineerId = engineerId;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
    }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public long getAuthVersion() { return authVersion; }
    public Long getCustomerId() { return customerId; }
    public Long getEngineerId() { return engineerId; }
    public List<Role> getRoles() { return roles; }
}
