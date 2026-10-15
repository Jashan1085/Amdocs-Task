package com.amdocs.telecom.dao.impl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcNotificationDAO {
    private final JdbcTemplate jdbc;
    public JdbcNotificationDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Long> pending(Instant now) {
        return jdbc.queryForList("SELECT notification_id FROM notification WHERE delivery_status IN ('PENDING','PROCESSING') "
                +"AND COALESCE(next_attempt_at,created_at)<=? ORDER BY notification_id LIMIT 100",Long.class,Timestamp.from(now));
    }
    public Optional<Map<String,Object>> lock(long id,Instant now) {
        return jdbc.queryForList("SELECT notification_id,attempts FROM notification WHERE notification_id=? "
                +"AND delivery_status IN ('PENDING','PROCESSING') AND COALESCE(next_attempt_at,created_at)<=? FOR UPDATE SKIP LOCKED",id,Timestamp.from(now)).stream().findFirst();
    }
    public void processing(long id) { jdbc.update("UPDATE notification SET delivery_status='PROCESSING',attempts=attempts+1 WHERE notification_id=?",id); }
    public void delivered(long id,Instant now) {
        jdbc.update("UPDATE notification SET delivery_status='DELIVERED',processed_at=?,next_attempt_at=NULL,last_error_code=NULL WHERE notification_id=?",Timestamp.from(now),id);
    }
    public void failed(long id,int attempts,Instant now) {
        jdbc.update("UPDATE notification SET delivery_status=?,processed_at=?,next_attempt_at=?,last_error_code='DELIVERY_UNAVAILABLE' WHERE notification_id=?",
                attempts>=3 ? "FAILED" : "PENDING",Timestamp.from(now),attempts>=3 ? null : Timestamp.from(now.plusSeconds(60)),id);
    }
    private String scope(String kind) {
        switch(kind) {
            case "CUSTOMER": return "n.customer_id=?";
            case "NETWORK_ENGINEER": return "n.engineer_id=? AND t.assigned_engineer_id=n.engineer_id";
            case "NETWORK_MANAGER": return "n.escalation_recipient_level IS NOT NULL";
            case "SERVICE_DESK": return "TRUE";
            default: throw new IllegalArgumentException("Unknown inbox scope");
        }
    }
    public List<Map<String,Object>> inbox(String kind,Long profile,boolean unread,int offset) {
        List<Object> args=new ArrayList<>(); if(profile!=null) { args.add(profile); }
        args.add(offset);
        return jdbc.queryForList("SELECT n.notification_id AS id,n.message,n.event_type,n.delivery_status,n.attempts,n.read_at,n.last_error_code,n.created_at,t.ticket_number "
                +"FROM notification n JOIN trouble_ticket t USING(ticket_id) WHERE "+scope(kind)+(unread ? " AND n.read_at IS NULL" : "")
                +" ORDER BY n.created_at DESC,n.notification_id DESC LIMIT 21 OFFSET ?",args.toArray());
    }
    public int read(long id,String kind,Long profile,Instant now) {
        List<Object> args=new ArrayList<>(Arrays.asList(Timestamp.from(now),id)); if(profile!=null) { args.add(profile); }
        return jdbc.update("UPDATE notification n SET read_at=COALESCE(n.read_at,?) FROM trouble_ticket t WHERE t.ticket_id=n.ticket_id AND n.notification_id=? AND "+scope(kind),args.toArray());
    }
}
