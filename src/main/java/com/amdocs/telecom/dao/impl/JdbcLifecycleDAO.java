package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcLifecycleDAO {
    private final JdbcTemplate jdbc;
    public JdbcLifecycleDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<String,Object> resolution(long id) {
        return jdbc.query("SELECT root_cause,resolution_text,resolution_code,resolver_engineer_id FROM ticket_resolution WHERE ticket_id=?",
                (rs,row) -> { Map<String,Object> result=new HashMap<>(); result.put("rootCause",rs.getString(1));
                    result.put("text",rs.getString(2)); result.put("code",rs.getString(3)); result.put("engineerId",rs.getLong(4));
                    return Collections.unmodifiableMap(result); },id).stream().findFirst().orElse(null);
    }
    public Map<String,Object> feedback(long id) {
        return jdbc.query("SELECT rating,comments,submitted_at FROM ticket_feedback WHERE ticket_id=?",
                (rs,row) -> { Map<String,Object> result=new HashMap<>(); result.put("rating",rs.getInt(1));
                    result.put("comments",rs.getString(2)); result.put("submittedAt",rs.getTimestamp(3).toInstant());
                    return Collections.unmodifiableMap(result); },id).stream().findFirst().orElse(null);
    }
    public Map<String,Object> cancellation(long id) {
        return jdbc.query("SELECT reason,cancelled_at FROM ticket_cancellation WHERE ticket_id=?",
                (rs,row) -> { Map<String,Object> result=new HashMap<>(); result.put("reason",rs.getString(1));
                    result.put("cancelledAt",rs.getTimestamp(2).toInstant()); return Collections.unmodifiableMap(result); },id)
                .stream().findFirst().orElse(null);
    }
    public boolean linkedEngineer(long engineerId,long userId) {
        return !jdbc.queryForList("SELECT engineer_id FROM network_engineer WHERE engineer_id=? AND user_id=? FOR SHARE",Long.class,engineerId,userId).isEmpty();
    }
    public boolean linkedCustomer(long customerId,long userId) {
        return !jdbc.queryForList("SELECT customer_id FROM customer WHERE customer_id=? AND user_id=?",Long.class,customerId,userId).isEmpty();
    }
    public int change(TroubleTicket ticket,TicketAction action,Instant time) {
        Timestamp stamp=Timestamp.from(time);
        return jdbc.update("UPDATE trouble_ticket SET status=CAST(? AS ticket_status),row_version=row_version+1,updated_at=?,"
                +"first_response_at=CASE WHEN ? THEN COALESCE(first_response_at,?) ELSE first_response_at END,"
                +"resolved_at=CASE WHEN ? THEN ? ELSE resolved_at END,closed_at=CASE WHEN ? THEN ? ELSE closed_at END "
                +"WHERE ticket_id=? AND row_version=?",action.getTarget().name(),stamp,action==TicketAction.ACKNOWLEDGE,stamp,
                action==TicketAction.RESOLVE,stamp,action==TicketAction.CLOSE,stamp,ticket.getTicketId(),ticket.getRowVersion());
    }
    public int touch(TroubleTicket ticket,Instant time) {
        return jdbc.update("UPDATE trouble_ticket SET row_version=row_version+1,updated_at=? WHERE ticket_id=? AND row_version=?",
                Timestamp.from(time),ticket.getTicketId(),ticket.getRowVersion());
    }
    public void resolution(long id,long engineerId,LifecycleForm form,Instant time) {
        jdbc.update("INSERT INTO ticket_resolution(ticket_id,root_cause,resolution_text,resolution_code,resolver_engineer_id,resolved_at) VALUES (?,?,?,?,?,?)",
                id,form.getRootCause().trim(),form.getResolutionText().trim(),form.getResolutionCode().name(),engineerId,Timestamp.from(time));
    }
    public void cancellation(long id,long userId,String reason,Instant time) {
        jdbc.update("INSERT INTO ticket_cancellation(ticket_id,reason,cancelled_by_user_id,cancelled_at) VALUES (?,?,?,?)",id,reason,userId,Timestamp.from(time));
    }
    public void feedback(long id,long customerId,FeedbackForm form,Instant time) {
        jdbc.update("INSERT INTO ticket_feedback(ticket_id,customer_id,rating,comments,submitted_at) VALUES (?,?,?,?,?)",
                id,customerId,form.getRating(),form.getComments()==null ? null : form.getComments().trim(),Timestamp.from(time));
    }
    public void history(TroubleTicket previous,TicketAction action,long userId,String reason,Instant time) {
        jdbc.update("INSERT INTO ticket_status_history(ticket_id,old_status,new_status,changed_by,changed_at,remarks) "
                +"VALUES (?,CAST(? AS ticket_status),CAST(? AS ticket_status),?,?,?)",previous.getTicketId(),previous.getStatus().name(),
                action.getTarget().name(),"user:"+userId,Timestamp.from(time),reason==null || reason.isEmpty() ? action.getLabel() : reason);
    }
    public void notifications(TroubleTicket ticket,long customerId,String event,String message,Instant time) {
        jdbc.update("INSERT INTO notification(customer_id,ticket_id,event_type,message,created_at) VALUES (?,?,?,?,?)",
                customerId,ticket.getTicketId(),event,message,Timestamp.from(time));
        if(ticket.getAssignedEngineerId()!=null) {
            jdbc.update("INSERT INTO notification(engineer_id,ticket_id,event_type,message,created_at) VALUES (?,?,?,?,?)",
                    ticket.getAssignedEngineerId(),ticket.getTicketId(),event,message,Timestamp.from(time));
        }
    }
    public void audit(long id,long userId,String action,String reason,Instant time) {
        jdbc.update("INSERT INTO audit_event(actor_user_id,ticket_id,action,reason,occurred_at) VALUES (?,?,?,?,?)",userId,id,action,reason,Timestamp.from(time));
    }
}
