package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcSlaDAO {
    private final JdbcTemplate jdbc;
    public JdbcSlaDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void monitorLockTimeout() { jdbc.execute("SET LOCAL lock_timeout = '5s'"); }
    public EscalationState state(long id,boolean lock) {
        return jdbc.query("SELECT highest_level,previous_operational_status FROM ticket_escalation_state WHERE ticket_id=?"+(lock ? " FOR UPDATE" : ""),
                (rs,row) -> new EscalationState(rs.getInt(1),rs.getString(2)==null ? null : TicketStatus.valueOf(rs.getString(2))),id)
                .stream().findFirst().orElse(new EscalationState(0,null));
    }
    public boolean recipient(int level) {
        return !jdbc.queryForList("SELECT escalation_level FROM escalation_recipient WHERE escalation_level=? AND enabled=TRUE FOR SHARE",Integer.class,level).isEmpty();
    }
    public void state(long id,int level,TicketStatus previous) {
        jdbc.update("INSERT INTO ticket_escalation_state(ticket_id,highest_level,previous_operational_status) VALUES (?,?,CAST(? AS ticket_status)) "
                +"ON CONFLICT(ticket_id) DO UPDATE SET highest_level=GREATEST(ticket_escalation_state.highest_level,EXCLUDED.highest_level),previous_operational_status=EXCLUDED.previous_operational_status",
                id,level,previous==null ? null : previous.name());
    }
    public void clearPrevious(long id) { jdbc.update("UPDATE ticket_escalation_state SET previous_operational_status=NULL WHERE ticket_id=?",id); }
    public int status(TroubleTicket ticket,TicketStatus target,Instant time) {
        return jdbc.update("UPDATE trouble_ticket SET status=CAST(? AS ticket_status),row_version=row_version+1,updated_at=? WHERE ticket_id=? AND row_version=?",
                target.name(),Timestamp.from(time),ticket.getTicketId(),ticket.getRowVersion());
    }
    public void statusHistory(TroubleTicket ticket,TicketStatus target,String actor,String reason,Instant time) {
        jdbc.update("INSERT INTO ticket_status_history(ticket_id,old_status,new_status,changed_by,remarks,changed_at) VALUES (?,CAST(? AS ticket_status),CAST(? AS ticket_status),?,?,?)",
                ticket.getTicketId(),ticket.getStatus().name(),target.name(),actor,reason,Timestamp.from(time));
    }
    public long escalation(long id,int level,TicketStatus previous,SlaEvaluation.Assessment assessment,Instant time) {
        return jdbc.queryForObject("INSERT INTO escalation_history(ticket_id,escalation_level,previous_operational_status,response_state,resolution_state,response_completed,resolution_completed,escalated_at) "
                +"VALUES (?,?,CAST(? AS ticket_status),?,?,?,?,?) RETURNING escalation_id",Long.class,id,level,previous==null ? null : previous.name(),
                assessment.getResponse().getState().name(),assessment.getResolution().getState().name(),assessment.getResponse().isCompleted(),assessment.getResolution().isCompleted(),Timestamp.from(time));
    }
    public void notifications(TroubleTicket ticket,long customer,long escalationId,int level,Instant time) {
        String event=level==1 ? "SLA_WARNING" : "SLA_BREACHED";
        String message="Ticket "+ticket.getTicketNumber()+" reached SLA escalation level "+level+".";
        notification(customer,null,null,ticket.getTicketId(),escalationId,event,message,time);
        if(ticket.getAssignedEngineerId()!=null) { notification(null,ticket.getAssignedEngineerId(),null,ticket.getTicketId(),escalationId,event,message,time); }
        notification(null,null,level,ticket.getTicketId(),escalationId,event,message,time);
    }
    private void notification(Long customer,Long engineer,Integer recipient,long ticket,long escalation,String event,String message,Instant time) {
        jdbc.update("INSERT INTO notification(customer_id,engineer_id,escalation_recipient_level,ticket_id,escalation_id,event_type,message,created_at) VALUES (?,?,?,?,?,?,?,?)",
                customer,engineer,recipient,ticket,escalation,event,message,Timestamp.from(time));
    }
    public void systemAudit(long id,int level,SlaEvaluation.Assessment assessment,Instant time) {
        jdbc.update("INSERT INTO audit_event(actor_kind,actor_user_id,ticket_id,action,reason,occurred_at) VALUES ('SYSTEM',NULL,?,?,?,?)",
                id,"SLA_ESCALATION_LEVEL_"+level,"Response="+assessment.getResponse().getState()+", resolution="+assessment.getResolution().getState(),Timestamp.from(time));
    }
    public int priority(TroubleTicket ticket,TicketPriority priority,SLAService.Targets targets,Instant time) {
        return jdbc.update("UPDATE trouble_ticket SET priority=CAST(? AS ticket_priority),sla_policy_version=?,response_deadline=?,resolution_deadline=?,row_version=row_version+1,updated_at=? WHERE ticket_id=? AND row_version=?",
                priority.name(),targets.getPolicy().getPolicyVersion(),Timestamp.from(targets.getResponseDeadline()),Timestamp.from(targets.getResolutionDeadline()),Timestamp.from(time),ticket.getTicketId(),ticket.getRowVersion());
    }
    public void priorityAudit(TroubleTicket ticket,TicketPriority priority,int version,long actor,String reason,Instant time) {
        jdbc.update("INSERT INTO audit_event(actor_user_id,ticket_id,action,reason,occurred_at,previous_priority,new_priority,previous_sla_version,new_sla_version) "
                +"VALUES (?,?,'PRIORITY_CHANGED',?,?,CAST(? AS ticket_priority),CAST(? AS ticket_priority),?,?)",actor,ticket.getTicketId(),reason,Timestamp.from(time),
                ticket.getPriority().name(),priority.name(),ticket.getSlaPolicyVersion(),version);
    }
    public List<Map<String,Object>> history(long id) {
        return jdbc.query("SELECT h.*,r.recipient_name FROM escalation_history h JOIN escalation_recipient r USING(escalation_level) WHERE h.ticket_id=? ORDER BY escalation_level LIMIT 3",
                (rs,row) -> { Map<String,Object> result=new HashMap<>(); result.put("level",rs.getInt("escalation_level")); result.put("recipient",rs.getString("recipient_name"));
                    result.put("responseState",rs.getString("response_state")); result.put("resolutionState",rs.getString("resolution_state"));
                    result.put("at",rs.getTimestamp("escalated_at").toInstant()); return Collections.unmodifiableMap(result); },id);
    }
    public List<SlaWorkItem> scan(int rank,long id) {
        return jdbc.query("SELECT ticket_id,priority FROM (SELECT ticket_id,priority,CASE priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END AS urgency "
                +"FROM trouble_ticket WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED')) pending WHERE (urgency,ticket_id)>(?,?) ORDER BY urgency,ticket_id LIMIT 200",
                (rs,row) -> new SlaWorkItem(rs.getLong(1),TicketPriority.valueOf(rs.getString(2))),rank,id);
    }
    public Map<String,Object> counters(Instant now) {
        return jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE t.first_response_at IS NULL AND n.at>=t.response_deadline) AS response_breached,"
                +"COUNT(*) FILTER(WHERE t.first_response_at IS NULL AND n.at<t.response_deadline AND n.at>=t.created_at+(t.response_deadline-t.created_at)*(p.warning_percent/100.0)) AS response_at_risk,"
                +"COUNT(*) FILTER(WHERE t.resolved_at IS NULL AND n.at>=t.resolution_deadline) AS resolution_breached,"
                +"COUNT(*) FILTER(WHERE t.resolved_at IS NULL AND n.at<t.resolution_deadline AND n.at>=t.created_at+(t.resolution_deadline-t.created_at)*(p.warning_percent/100.0)) AS resolution_at_risk,"
                +"COUNT(*) FILTER(WHERE t.status='ESCALATED') AS escalated FROM trouble_ticket t JOIN sla_configuration p ON (p.priority,p.policy_version)=(t.priority,t.sla_policy_version) "
                +"CROSS JOIN (SELECT CAST(? AS TIMESTAMPTZ) AS at) n WHERE t.status NOT IN ('RESOLVED','CLOSED','CANCELLED')",Timestamp.from(now));
    }
    public static final class EscalationState {
        private final int level;
        private final TicketStatus previousStatus;
        public EscalationState(int level,TicketStatus previousStatus) { this.level=level; this.previousStatus=previousStatus; }
        public int getLevel() { return level; }
        public TicketStatus getPreviousStatus() { return previousStatus; }
    }
}
