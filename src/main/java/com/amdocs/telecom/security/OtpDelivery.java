package com.amdocs.telecom.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Replace this adapter with email delivery when that integration is authorized. */
public interface OtpDelivery {
    void deliver(UUID id, String binding, String code, Instant expiry);
    default Optional<String> preview(UUID id, String binding) { return Optional.empty(); }
    default void forget(UUID id) { }
    default boolean isDemo() { return false; }
}
