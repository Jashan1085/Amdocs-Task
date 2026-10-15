package com.amdocs.telecom.model;

/** Controlled values aligned with the PostgreSQL baseline. */
public enum TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    PENDING_CUSTOMER,
    ESCALATED,
    RESOLVED,
    CLOSED,
    CANCELLED
}
