package com.amdocs.telecom.assessment;
import com.amdocs.telecom.model.TroubleTicket;
import java.time.Duration;
@FunctionalInterface
public interface TicketMetric {
    double measure(TroubleTicket ticket);
    default String units() { return "minutes"; }
    static TicketMetric resolutionMinutes() { return ticket -> Duration.between(ticket.getCreatedAt(),ticket.getResolvedAt()).toMillis()/60000.0; }
}
