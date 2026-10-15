package com.amdocs.telecom.model;

import java.time.Instant;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class SLAConfiguration {

    private final TicketPriority priority;
    private final int policyVersion;
    private final int responseMinutes;
    private final int resolutionMinutes;
    private final int warningPercent;
    private final Instant createdAt;

    public SLAConfiguration(
            TicketPriority priority,
            int policyVersion,
            int responseMinutes,
            int resolutionMinutes,
            int warningPercent,
            Instant createdAt) {
        this.priority = priority;
        this.policyVersion = policyVersion;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.warningPercent = warningPercent;
        this.createdAt = createdAt;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public int getWarningPercent() {
        return warningPercent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
