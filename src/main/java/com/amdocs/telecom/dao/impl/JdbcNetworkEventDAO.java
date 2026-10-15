package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcNetworkEventDAO {
    private final JdbcTemplate jdbc;
    public JdbcNetworkEventDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long node(NetworkNodeForm form) {
        List<Long> rows=jdbc.queryForList("INSERT INTO network_node(node_code,node_name,region) VALUES (?,?,?) ON CONFLICT(node_code) DO NOTHING RETURNING node_id",Long.class,
                form.getCode().trim(),form.getName().trim(),form.getRegion().trim());
        return rows.isEmpty() ? 0 : rows.get(0);
    }
    public boolean node(long id,boolean exclusive) {
        return !jdbc.queryForList("SELECT node_id FROM network_node WHERE node_id=? AND active=TRUE FOR "+(exclusive ? "UPDATE" : "SHARE"),Long.class,id).isEmpty();
    }
    public List<Map<String,Object>> nodes(int offset) { return jdbc.queryForList("SELECT node_id AS id,node_code,node_name,region,active FROM network_node ORDER BY node_id DESC LIMIT 21 OFFSET ?",offset); }
    public Optional<Long> service(String code) { return jdbc.queryForList("SELECT service_id FROM telecom_service WHERE service_code=?",Long.class,code).stream().findFirst(); }
    public int link(long node,long service) { return jdbc.update("INSERT INTO service_network_node(node_id,service_id) VALUES (?,?) ON CONFLICT DO NOTHING",node,service); }
    public List<Map<String,Object>> mappings(long id,int offset) {
        return jdbc.queryForList("SELECT s.service_id,s.service_code,s.service_name,s.service_status FROM telecom_service s JOIN service_network_node n USING(service_id) "
                +"WHERE n.node_id=? ORDER BY s.service_id LIMIT 21 OFFSET ?",id,offset);
    }
    public long insert(NetworkEventForm form,long user,Instant now) {
        jdbc.update("INSERT INTO network_event(event_key,node_id,event_type,description,created_by,created_at) VALUES (?,?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING",
                form.getEventKey(),form.getNodeId(),form.getType().name(),form.getDescription().trim(),user,Timestamp.from(now));
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM network_event WHERE event_key=?",form.getEventKey());
        if(((Number)row.get("node_id")).longValue()!=form.getNodeId() || !row.get("event_type").equals(form.getType().name())
                || !row.get("description").equals(form.getDescription().trim()) || ((Number)row.get("created_by")).longValue()!=user) { return 0; }
        return ((Number)row.get("event_id")).longValue();
    }
    public Optional<Map<String,Object>> lock(long id,Instant now) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        return jdbc.queryForList("SELECT * FROM network_event WHERE event_id=? AND processing_status IN ('PENDING','PROCESSING') "
                +"AND COALESCE(next_attempt_at,created_at)<=? FOR UPDATE SKIP LOCKED",id,Timestamp.from(now)).stream().findFirst();
    }
    public void processing(long id) { jdbc.update("UPDATE network_event SET processing_status='PROCESSING',attempts=attempts+1 WHERE event_id=?",id); }
    public List<Long> services(long node,long after) {
        return jdbc.queryForList("SELECT service_id FROM service_network_node WHERE node_id=? AND service_id>? ORDER BY service_id LIMIT 50",Long.class,node,after);
    }
    public boolean linked(long event,long service) { return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM event_ticket WHERE event_id=? AND service_id=?)",Boolean.class,event,service); }
    public void ticket(long event,long service,long ticket) { jdbc.update("INSERT INTO event_ticket(event_id,service_id,ticket_id) VALUES (?,?,?)",event,service,ticket); }
    public void complete(long id,Instant now) {
        jdbc.update("UPDATE network_event SET processing_status=CASE WHEN EXISTS(SELECT 1 FROM event_ticket WHERE event_id=?) THEN 'PROCESSED' ELSE 'UNMAPPED' END,processed_at=?,next_attempt_at=NULL,last_error_code=NULL WHERE event_id=?",id,Timestamp.from(now),id);
    }
    public void failure(long id,int attempts,Instant now) {
        jdbc.update("UPDATE network_event SET processing_status=?,last_error_code='EVENT_PROCESSING_UNAVAILABLE',processed_at=?,next_attempt_at=? WHERE event_id=?",
                attempts>=3 ? "FAILED" : "PENDING",Timestamp.from(now),attempts>=3 ? null : Timestamp.from(now.plusSeconds(60)),id);
    }
    public List<Long> pending(Instant now) { return jdbc.queryForList("SELECT event_id FROM network_event WHERE processing_status IN ('PENDING','PROCESSING') AND COALESCE(next_attempt_at,created_at)<=? ORDER BY event_id LIMIT 256",Long.class,Timestamp.from(now)); }
    public int retry(long id) { return jdbc.update("UPDATE network_event SET processing_status='PENDING',attempts=0,retry_count=retry_count+1,next_attempt_at=NULL,processed_at=NULL,last_error_code=NULL WHERE event_id=? AND processing_status IN ('UNMAPPED','FAILED')",id); }
    public List<Map<String,Object>> events(int offset) {
        return jdbc.queryForList("SELECT e.event_id AS id,e.event_key,e.node_id,n.node_code,e.event_type,e.description,e.processing_status,e.attempts,e.retry_count,e.created_at,e.last_error_code "
                +"FROM network_event e JOIN network_node n USING(node_id) ORDER BY e.event_id DESC LIMIT 21 OFFSET ?",offset);
    }
    public Optional<Map<String,Object>> event(long id) { return jdbc.queryForList("SELECT e.*,n.node_code FROM network_event e JOIN network_node n USING(node_id) WHERE event_id=?",id).stream().findFirst(); }
    public List<Map<String,Object>> tickets(long id,int offset) {
        return jdbc.queryForList("SELECT t.ticket_id,t.ticket_number,t.status,s.service_code FROM event_ticket e JOIN trouble_ticket t USING(ticket_id) "
                +"JOIN telecom_service s ON s.service_id=e.service_id WHERE e.event_id=? ORDER BY e.service_id LIMIT 21 OFFSET ?",id,offset);
    }
}
