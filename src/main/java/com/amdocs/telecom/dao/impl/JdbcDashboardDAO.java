package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
@Repository
public class JdbcDashboardDAO {
    private final JdbcTemplate jdbc;
    public JdbcDashboardDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private String scope(Long customer,Long engineer,List<Object> args) {
        if(customer!=null) { args.add(customer); return "s.customer_id=?"; }
        if(engineer!=null) { args.add(engineer); return "t.assigned_engineer_id=?"; }
        return "TRUE";
    }
    public Map<String,Object> summary(Long customer,Long engineer,Instant now) {
        List<Object> args=new ArrayList<>();
        Instant day=now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        args.add(Timestamp.from(now)); args.add(Timestamp.from(now)); args.add(Timestamp.from(day)); args.add(Timestamp.from(day.plusSeconds(86400)));
        String filter=scope(customer,engineer,args);
        return jdbc.queryForMap("SELECT COUNT(*) AS total,COUNT(*) FILTER(WHERE t.status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS active,"
                +"COUNT(*) FILTER(WHERE t.status='OPEN') AS open,COUNT(*) FILTER(WHERE t.priority='CRITICAL' AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS critical,"
                +"COUNT(*) FILTER(WHERE t.status='ESCALATED') AS escalated,COUNT(*) FILTER(WHERE t.status='PENDING_CUSTOMER') AS pending_customer,"
                +"COUNT(*) FILTER(WHERE t.first_response_at IS NULL AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED') AND t.response_deadline<=?) AS response_overdue,"
                +"COUNT(*) FILTER(WHERE t.resolved_at IS NULL AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED') AND t.resolution_deadline<=?) AS resolution_overdue,"
                +"COUNT(*) FILTER(WHERE t.resolved_at>=? AND t.resolved_at<?) AS resolved_today,"
                +"ROUND(AVG(EXTRACT(EPOCH FROM(t.resolved_at-t.created_at))/3600),2) AS average_resolution_hours "
                +"FROM trouble_ticket t JOIN telecom_service s USING(service_id) WHERE "+filter,args.toArray());
    }
    public List<Map<String,Object>> recent(Long customer,Long engineer) {
        List<Object> args=new ArrayList<>(); String filter=scope(customer,engineer,args);
        return jdbc.queryForList("SELECT t.ticket_id,t.ticket_number,t.priority,t.status,s.service_code FROM trouble_ticket t JOIN telecom_service s USING(service_id) WHERE "+filter+" ORDER BY t.updated_at DESC,t.ticket_id DESC LIMIT 5",args.toArray());
    }
    public Map<String,Object> extras(Long customer,Long engineer,Role role) {
        String serviceFilter=customer!=null ? "customer_id=?" : engineer!=null ? "EXISTS(SELECT 1 FROM trouble_ticket t WHERE t.service_id=telecom_service.service_id AND t.assigned_engineer_id=?)" : "TRUE";
        List<Object> args=new ArrayList<>(); if(customer!=null || engineer!=null) { args.add(customer!=null ? customer : engineer); }
        Map<String,Object> result=new HashMap<>(); result.put("active_services",jdbc.queryForObject("SELECT COUNT(*) FROM telecom_service WHERE service_status='ACTIVE' AND "+serviceFilter,Long.class,args.toArray()));
        String recipient;
        if(role==Role.CUSTOMER) { recipient="n.customer_id=?"; }
        else if(role==Role.NETWORK_ENGINEER) { recipient="n.engineer_id=? AND t.assigned_engineer_id=n.engineer_id"; }
        else { recipient=role==Role.NETWORK_MANAGER ? "n.escalation_recipient_level IS NOT NULL" : "TRUE"; }
        result.putAll(jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE n.read_at IS NULL) AS unread,COUNT(*) FILTER(WHERE n.delivery_status='FAILED') AS notification_failed FROM notification n JOIN trouble_ticket t USING(ticket_id) WHERE "+recipient,args.toArray()));
        if(customer==null && engineer==null) { result.putAll(jdbc.queryForMap("SELECT COUNT(*) FILTER(WHERE processing_status='UNMAPPED') AS unmapped_events,COUNT(*) FILTER(WHERE processing_status='FAILED') AS failed_events FROM network_event")); }
        return result;
    }
    public List<Map<String,Object>> workload() {
        return jdbc.queryForList("SELECT e.employee_code,e.engineer_name,COUNT(t.ticket_id) AS active_tickets FROM network_engineer e LEFT JOIN trouble_ticket t ON t.assigned_engineer_id=e.engineer_id AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED') GROUP BY e.engineer_id ORDER BY active_tickets DESC,e.employee_code LIMIT 5");
    }
}
