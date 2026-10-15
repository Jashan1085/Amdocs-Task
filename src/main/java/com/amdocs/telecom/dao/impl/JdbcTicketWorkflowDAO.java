package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.mapper.*;
import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.service.SLAService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcTicketWorkflowDAO {
    private final JdbcTemplate jdbc;
    public JdbcTicketWorkflowDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<TelecomService> lockService(long id) {
        List<TelecomService> rows = jdbc.query("SELECT s.* FROM telecom_service s JOIN customer c ON c.customer_id=s.customer_id "
                + "WHERE s.service_id=? FOR SHARE OF s,c", new TelecomServiceRowMapper(), id);
        return rows.stream().findFirst();
    }
    public List<TroubleTicket> tickets(Long customerId, TicketStatus status, TicketPriority priority, PageRequest page) {
        return filteredTickets(customerId,null,status,priority,page);
    }
    public List<TroubleTicket> engineerTickets(long engineerId,TicketStatus status,TicketPriority priority,PageRequest page) {
        return filteredTickets(null,engineerId,status,priority,page);
    }
    private List<TroubleTicket> filteredTickets(Long customerId,Long engineerId,TicketStatus status,TicketPriority priority,PageRequest page) {
        StringBuilder sql = new StringBuilder("SELECT t.* FROM trouble_ticket t JOIN telecom_service s ON s.service_id=t.service_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (customerId != null) { sql.append(" AND s.customer_id=?"); args.add(customerId); }
        if (engineerId != null) { sql.append(" AND t.assigned_engineer_id=?"); args.add(engineerId); }
        if (status != null) { sql.append(" AND t.status=CAST(? AS ticket_status)"); args.add(status.name()); }
        if (priority != null) { sql.append(" AND t.priority=CAST(? AS ticket_priority)"); args.add(priority.name()); }
        sql.append(" ORDER BY t.created_at DESC,t.ticket_id DESC LIMIT ? OFFSET ?");
        args.add(page.getLimit()); args.add(page.getOffset());
        return jdbc.query(sql.toString(), new TroubleTicketRowMapper(), args.toArray());
    }
    public long insert(TicketCreateForm form, SLAService.Targets sla) {
        Long sequence = jdbc.queryForObject("SELECT nextval('ticket_number_seq')", Long.class);
        String number = String.format(Locale.ROOT, "TT-%010d", sequence);
        return jdbc.queryForObject("INSERT INTO trouble_ticket (ticket_number,service_id,category,description,priority,severity,"
                        + "sla_policy_version,status,response_deadline,resolution_deadline,created_at,updated_at) "
                        + "VALUES (?,?,CAST(? AS incident_category),?,CAST(? AS ticket_priority),?,?, 'OPEN',?,?,?,?) RETURNING ticket_id",
                Long.class, number, form.getServiceId(), form.getCategory().name(), form.getDescription().trim(),
                form.getPriority().name(), form.getSeverity().name(), sla.getPolicy().getPolicyVersion(),
                Timestamp.from(sla.getResponseDeadline()), Timestamp.from(sla.getResolutionDeadline()),
                Timestamp.from(sla.getCreatedAt()), Timestamp.from(sla.getCreatedAt()));
    }
    public void initialHistory(long ticketId, long actorId, Instant time) {
        jdbc.update("INSERT INTO ticket_status_history (ticket_id,old_status,new_status,changed_by,changed_at,remarks) "
                        + "VALUES (?,NULL,'OPEN',?,?,?)", ticketId, "user:" + actorId, Timestamp.from(time), "Ticket created");
    }
    public void notifyCustomer(long ticketId, long customerId, String number, Instant time) {
        jdbc.update("INSERT INTO notification (customer_id,ticket_id,event_type,message,created_at) VALUES (?,?,'TICKET_CREATED',?,?)",
                customerId, ticketId, "Ticket " + number + " has been created.", Timestamp.from(time));
    }
    public void audit(long ticketId, long actorId, Instant time) {
        jdbc.update("INSERT INTO audit_event (actor_user_id,ticket_id,action,occurred_at) VALUES (?,?,'TICKET_CREATED',?)",
                actorId, ticketId, Timestamp.from(time));
    }
    public void eventHistory(long ticketId,long eventId,Instant time) {
        jdbc.update("INSERT INTO ticket_status_history(ticket_id,old_status,new_status,changed_by,changed_at,remarks) VALUES (?,NULL,'OPEN',?,?,?)",
                ticketId,"SYSTEM:NETWORK_EVENT:"+eventId,Timestamp.from(time),"Network event ticket created");
    }
    public void eventAudit(long ticketId,long eventId,Instant time) {
        jdbc.update("INSERT INTO audit_event(actor_kind,actor_user_id,ticket_id,action,reason,occurred_at) VALUES ('SYSTEM',NULL,?,'TICKET_CREATED',?,?)",
                ticketId,"Network event "+eventId,Timestamp.from(time));
    }
}
