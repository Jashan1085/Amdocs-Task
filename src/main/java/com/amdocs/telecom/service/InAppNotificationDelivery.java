package com.amdocs.telecom.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;

/** Durable local inbox receipt; no external email or network side effect. */
@Component
public class InAppNotificationDelivery implements NotificationDelivery {
    private final JdbcTemplate jdbc;
    public InAppNotificationDelivery(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public void deliver(long id,Instant now) {
        jdbc.update("INSERT INTO notification_receipt(notification_id,delivered_at) VALUES (?,?) ON CONFLICT(notification_id) DO NOTHING",id,Timestamp.from(now));
    }
}
