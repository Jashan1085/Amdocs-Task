package com.amdocs.telecom.model;

import java.time.Instant;

/** Immutable persistence snapshot; lifecycle validation belongs to business services. */
public final class TroubleTicket {

    private final long ticketId;
    private final String ticketNumber;
    private final long serviceId;
    private final Long assignedEngineerId;
    private final IncidentCategory category;
    private final String description;
    private final TicketPriority priority;
    private final Severity severity;
    private final int slaPolicyVersion;
    private final TicketStatus status;
    private final Instant responseDeadline;
    private final Instant resolutionDeadline;
    private final Instant firstResponseAt;
    private final Instant resolvedAt;
    private final Instant closedAt;
    private final long rowVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    public TroubleTicket(
            long ticketId,
            String ticketNumber,
            long serviceId,
            Long assignedEngineerId,
            IncidentCategory category,
            String description,
            TicketPriority priority,
            Severity severity,
            int slaPolicyVersion,
            TicketStatus status,
            Instant responseDeadline,
            Instant resolutionDeadline,
            Instant firstResponseAt,
            Instant resolvedAt,
            Instant closedAt,
            long rowVersion,
            Instant createdAt,
            Instant updatedAt) {
        this.ticketId = ticketId;
        this.ticketNumber = ticketNumber;
        this.serviceId = serviceId;
        this.assignedEngineerId = assignedEngineerId;
        this.category = category;
        this.description = description;
        this.priority = priority;
        this.severity = severity;
        this.slaPolicyVersion = slaPolicyVersion;
        this.status = status;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
        this.firstResponseAt = firstResponseAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
        this.rowVersion = rowVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getTicketId() {
        return ticketId;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public long getServiceId() {
        return serviceId;
    }

    public Long getAssignedEngineerId() {
        return assignedEngineerId;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public Severity getSeverity() {
        return severity;
    }

    public int getSlaPolicyVersion() {
        return slaPolicyVersion;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getResponseDeadline() {
        return responseDeadline;
    }

    public Instant getResolutionDeadline() {
        return resolutionDeadline;
    }

    public Instant getFirstResponseAt() {
        return firstResponseAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
