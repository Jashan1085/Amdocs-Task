package com.amdocs.telecom.model;

import java.time.Instant;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class TicketStatusHistory {

    private final long historyId;
    private final long ticketId;
    private final TicketStatus oldStatus;
    private final TicketStatus newStatus;
    private final String changedBy;
    private final Instant changedAt;
    private final String remarks;

    public TicketStatusHistory(
            long historyId,
            long ticketId,
            TicketStatus oldStatus,
            TicketStatus newStatus,
            String changedBy,
            Instant changedAt,
            String remarks) {
        this.historyId = historyId;
        this.ticketId = ticketId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.remarks = remarks;
    }

    public long getHistoryId() {
        return historyId;
    }

    public long getTicketId() {
        return ticketId;
    }

    public TicketStatus getOldStatus() {
        return oldStatus;
    }

    public TicketStatus getNewStatus() {
        return newStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getRemarks() {
        return remarks;
    }
}
