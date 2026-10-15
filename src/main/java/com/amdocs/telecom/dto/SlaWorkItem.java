package com.amdocs.telecom.dto;
import com.amdocs.telecom.model.TicketPriority;
public final class SlaWorkItem {
    private final long ticketId;
    private final TicketPriority priority;
    public SlaWorkItem(long ticketId,TicketPriority priority) { this.ticketId=ticketId; this.priority=priority; }
    public long getTicketId() { return ticketId; }
    public TicketPriority getPriority() { return priority; }
    public int getRank() {
        switch(priority) { case CRITICAL: return 1; case HIGH: return 2; case MEDIUM: return 3; default: return 4; }
    }
}
