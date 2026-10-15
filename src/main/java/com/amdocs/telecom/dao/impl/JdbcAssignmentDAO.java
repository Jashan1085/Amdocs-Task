package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.mapper.*;
import com.amdocs.telecom.dto.EngineerRecommendation;
import com.amdocs.telecom.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcAssignmentDAO {
    private final JdbcTemplate jdbc;
    public JdbcAssignmentDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<TroubleTicket> lockTicket(long id) {
        return jdbc.query("SELECT * FROM trouble_ticket WHERE ticket_id=? FOR UPDATE",new TroubleTicketRowMapper(),id).stream().findFirst();
    }
    public Optional<String> specialization(IncidentCategory category,boolean lock) {
        return jdbc.query("SELECT specialization FROM category_specialization WHERE category=CAST(? AS incident_category)"
                +(lock ? " FOR SHARE" : ""),(rs,row) -> rs.getString(1),category.name()).stream().findFirst();
    }
    public List<EngineerRecommendation> recommendations(String skill,String region,Long currentEngineer) {
        String sql="SELECT e.*, (SELECT COUNT(*) FROM trouble_ticket t WHERE t.assigned_engineer_id=e.engineer_id "
                +"AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS active_workload FROM network_engineer e "
                +"WHERE e.available=TRUE AND e.specialization=? AND e.region=?"
                +(currentEngineer==null ? "" : " AND e.engineer_id<>?")
                +" ORDER BY active_workload ASC,e.experience_years DESC,e.employee_code COLLATE \"C\" ASC LIMIT 3";
        Object[] args=currentEngineer==null ? new Object[]{skill,region} : new Object[]{skill,region,currentEngineer};
        NetworkEngineerRowMapper mapper=new NetworkEngineerRowMapper();
        return jdbc.query(sql,(rs,row) -> new EngineerRecommendation(mapper.mapRow(rs,row),rs.getLong("active_workload")),args);
    }
    public List<NetworkEngineer> lockEngineers(long target,Long previous) {
        // Stable order avoids deadlocks when two tickets swap engineers.
        return jdbc.query("SELECT * FROM network_engineer WHERE engineer_id IN (?,?) ORDER BY engineer_id FOR UPDATE",
                new NetworkEngineerRowMapper(),target,previous);
    }
    public int assign(TroubleTicket ticket,long engineerId,TicketStatus status,Instant time) {
        return jdbc.update("UPDATE trouble_ticket SET assigned_engineer_id=?,status=CAST(? AS ticket_status),"
                +"row_version=row_version+1,updated_at=? WHERE ticket_id=? AND row_version=?",engineerId,status.name(),
                Timestamp.from(time),ticket.getTicketId(),ticket.getRowVersion());
    }
    public void statusHistory(long id,long actorId,Instant time) {
        jdbc.update("INSERT INTO ticket_status_history (ticket_id,old_status,new_status,changed_by,changed_at,remarks) "
                +"VALUES (?,'OPEN','ASSIGNED',?,?,?)",id,"user:"+actorId,Timestamp.from(time),"Engineer assigned");
    }
    public void notifications(long id,long customerId,long engineerId,String number,Instant time) {
        jdbc.update("INSERT INTO notification (customer_id,ticket_id,event_type,message,created_at) VALUES (?,?,'TICKET_ASSIGNED',?,?)",
                customerId,id,"Engineer assignment updated for ticket "+number+".",Timestamp.from(time));
        jdbc.update("INSERT INTO notification (engineer_id,ticket_id,event_type,message,created_at) VALUES (?,?,'TICKET_ASSIGNED',?,?)",
                engineerId,id,"You have been assigned ticket "+number+".",Timestamp.from(time));
    }
    public void audit(TroubleTicket previous,long target,long actorId,String reason,Instant time) {
        jdbc.update("INSERT INTO audit_event (actor_user_id,ticket_id,action,occurred_at,previous_engineer_id,new_engineer_id,reason) "
                +"VALUES (?,?,?,?,?,?,?)",actorId,previous.getTicketId(),previous.getAssignedEngineerId()==null ? "TICKET_ASSIGNED" : "TICKET_REASSIGNED",
                Timestamp.from(time),previous.getAssignedEngineerId(),target,reason);
    }
}
