package com.amdocs.telecom.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Demo codes stay in bounded server memory; no general log or public identifier lookup. */
@Component
@Profile("dev")
public class DevOtpDelivery implements OtpDelivery {
    private final ConcurrentMap<UUID, Message> messages = new ConcurrentHashMap<>();
    private final Clock clock;
    public DevOtpDelivery(Clock clock) { this.clock = clock; }
    @Override public synchronized void deliver(UUID id, String binding, String code, Instant expiry) {
        messages.entrySet().removeIf(entry -> !clock.instant().isBefore(entry.getValue().expiry));
        if (messages.size() >= 1000) { throw new OtpDeliveryUnavailableException(); }
        messages.put(id, new Message(SecurityHash.sha256(binding), code, expiry));
    }
    @Override public Optional<String> preview(UUID id, String binding) {
        Message value = messages.get(id);
        if (value == null) { return Optional.empty(); }
        if (!clock.instant().isBefore(value.expiry)) { messages.remove(id); return Optional.empty(); }
        return SecurityHash.matches(value.bindingHash, binding) ? Optional.of(value.code) : Optional.empty();
    }
    @Override public void forget(UUID id) { messages.remove(id); }
    @Override public boolean isDemo() { return true; }
    private static final class Message {
        private final String bindingHash;
        private final String code;
        private final Instant expiry;
        private Message(String bindingHash, String code, Instant expiry) {
            this.bindingHash = bindingHash; this.code = code; this.expiry = expiry;
        }
    }
}
