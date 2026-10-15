package com.amdocs.telecom.model;

public enum TicketAction {
    ACKNOWLEDGE("Acknowledge / start work",TicketStatus.IN_PROGRESS),
    WAIT_CUSTOMER("Request customer information",TicketStatus.PENDING_CUSTOMER),
    RESUME("Resume investigation",TicketStatus.IN_PROGRESS),
    RESOLVE("Resolve ticket",TicketStatus.RESOLVED),
    CLOSE("Close ticket",TicketStatus.CLOSED),
    CANCEL("Cancel ticket",TicketStatus.CANCELLED);
    private final String label;
    private final TicketStatus target;
    TicketAction(String label,TicketStatus target) { this.label=label; this.target=target; }
    public String getLabel() { return label; }
    public TicketStatus getTarget() { return target; }
}
