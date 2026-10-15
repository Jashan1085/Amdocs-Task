package com.amdocs.telecom.service;
import java.time.Instant;

/** Delivery participates in the caller's JDBC transaction and must be idempotent. */
public interface NotificationDelivery {
    void deliver(long notificationId, Instant now);
}
