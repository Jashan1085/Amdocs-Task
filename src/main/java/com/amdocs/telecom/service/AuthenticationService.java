package com.amdocs.telecom.service;

import com.amdocs.telecom.dao.impl.JdbcAuthenticationDAO;
import com.amdocs.telecom.security.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final JdbcAuthenticationDAO dao;
    private final PasswordEncoder encoder;
    private final OtpDelivery delivery;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthenticationService(JdbcAuthenticationDAO dao, PasswordEncoder encoder, OtpDelivery delivery,
                                 Clock clock, PlatformTransactionManager manager) {
        this.dao = dao; this.encoder = encoder; this.delivery = delivery; this.clock = clock;
        transaction = new TransactionTemplate(manager);
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public Optional<PendingChallenge> beginLogin(String username, String password, String binding) {
        return transaction.execute(status -> {
            Instant now = clock.instant();
            String name = normalize(username);
            AuthAccount user = dao.findByUsername(name, true).orElse(null);
            boolean passwordMatches = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                    && encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
            if (user == null) {
                dao.record(null, name, "PASSWORD_FAILURE", now);
                return Optional.empty();
            }
            if (locked(user, now)) {
                dao.record(user.getUserId(), name, "LOCKED", now);
                return Optional.empty();
            }
            int attempts = user.getLockedUntil() == null ? user.getFailedAttempts() : 0;
            if (!passwordMatches || !canAuthenticate(user)) {
                int failures = Math.min(5, attempts + 1);
                dao.updateFailures(user.getUserId(), failures,
                        failures == 5 ? now.plus(Duration.ofMinutes(15)) : null, now);
                dao.record(user.getUserId(), name, "PASSWORD_FAILURE", now);
                return Optional.empty();
            }
            if (user.getLockedUntil() != null) { dao.updateFailures(user.getUserId(), 0, null, now); }
            return Optional.of(issue(user, OtpPurpose.LOGIN, binding, now));
        });
    }

    /** Same outward flow for missing/disabled accounts: no account-existence response. */
    public PendingChallenge beginReset(String username, String binding) {
        try {
            return transaction.execute(status -> {
            Instant now = clock.instant();
            String name = normalize(username);
            AuthAccount user = dao.findByUsername(name, true).orElse(null);
            dao.record(user == null ? null : user.getUserId(), name, "RESET_REQUESTED", now);
            return user == null || !user.isEnabled()
                    ? new PendingChallenge(UUID.randomUUID(), OtpPurpose.RESET, now.plus(Duration.ofMinutes(5)))
                    : issue(user, OtpPurpose.RESET, binding, now);
            });
        } catch (OtpDeliveryUnavailableException unavailable) {
            // Preserve the same reset response for known and unknown usernames even
            // when delivery is not configured. The failed issue transaction rolled back.
            return new PendingChallenge(UUID.randomUUID(), OtpPurpose.RESET, clock.instant().plus(Duration.ofMinutes(5)));
        }
    }

    public Optional<TelecomPrincipal> completeLogin(PendingChallenge pending, String binding, String code) {
        Optional<TelecomPrincipal> result = transaction.execute(status -> {
            Instant now = clock.instant();
            AuthAccount user = verify(pending, binding, code, OtpPurpose.LOGIN, now);
            if (user == null) { return Optional.empty(); }
            dao.consumeChallenge(pending.getId(), now);
            dao.updateFailures(user.getUserId(), 0, null, now);
            dao.record(user.getUserId(), user.getUsername(), "SUCCESS", now);
            return Optional.of(new TelecomPrincipal(user));
        });
        if (result.isPresent()) { delivery.forget(pending.getId()); }
        return result;
    }

    public boolean resetPassword(PendingChallenge pending, String binding, String code, String newPassword) {
        if (!PasswordPolicy.accepts(newPassword)) { return false; }
        boolean result = Boolean.TRUE.equals(transaction.execute(status -> {
            Instant now = clock.instant();
            AuthAccount user = verify(pending, binding, code, OtpPurpose.RESET, now);
            if (user == null) { return false; }
            dao.changePassword(user.getUserId(), encoder.encode(newPassword), now);
            dao.invalidateChallenges(user.getUserId(), null, now);
            dao.record(user.getUserId(), user.getUsername(), "RESET_SUCCESS", now);
            return true;
        }));
        if (result) { delivery.forget(pending.getId()); }
        return result;
    }

    public void captchaFailure(String username) {
        dao.record(null, normalize(username), "CAPTCHA_FAILURE", clock.instant());
    }

    private PendingChallenge issue(AuthAccount user, OtpPurpose purpose, String binding, Instant now) {
        if (binding == null || binding.length() < 16) { throw new IllegalArgumentException("Missing session binding"); }
        UUID id = UUID.randomUUID();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        Instant expiry = now.plus(Duration.ofMinutes(5));
        dao.invalidateChallenges(user.getUserId(), purpose, now);
        dao.insertChallenge(id, user, purpose, SessionSecrets.hash(binding), encoder.encode(code), now, expiry);
        delivery.deliver(id, binding, code, expiry);
        return new PendingChallenge(id, purpose, expiry);
    }

    private AuthAccount verify(PendingChallenge pending, String binding, String code, OtpPurpose purpose, Instant now) {
        if (pending == null || pending.getPurpose() != purpose || binding == null) { return null; }
        OtpRecord initial = dao.findChallenge(pending.getId(), false).orElse(null);
        if (initial == null) { return null; }
        // Every workflow locks user first, then challenge, to serialize attempts/reset.
        AuthAccount user = dao.findById(initial.getUserId(), true).orElse(null);
        OtpRecord challenge = dao.findChallenge(pending.getId(), true).orElse(null);
        if (user == null || challenge == null || challenge.getPurpose() != purpose
                || !SessionSecrets.matches(challenge.getBindingHash(), binding)
                || challenge.getConsumedAt() != null || challenge.getAttempts() >= 5) { return null; }
        if (!now.isBefore(challenge.getExpiresAt()) || !user.isEnabled()
                || user.getAuthVersion() != challenge.getAuthVersion()
                || (purpose == OtpPurpose.LOGIN && (!canAuthenticate(user) || locked(user, now)))) {
            dao.consumeChallenge(pending.getId(), now);
            dao.record(user.getUserId(), user.getUsername(), "OTP_FAILURE", now);
            return null;
        }
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, challenge.getCodeHash())) {
            dao.failChallenge(pending.getId(), now);
            dao.record(user.getUserId(), user.getUsername(), "OTP_FAILURE", now);
            return null;
        }
        return user;
    }

    private static boolean canAuthenticate(AuthAccount user) {
        return user.isEnabled() && !user.getRoles().isEmpty()
                && (!user.getRoles().contains(Role.CUSTOMER) || user.getCustomerId() != null)
                && (!user.getRoles().contains(Role.NETWORK_ENGINEER) || user.getEngineerId() != null);
    }
    private static boolean locked(AuthAccount user, Instant now) {
        return user.getLockedUntil() != null && now.isBefore(user.getLockedUntil());
    }
    private static String normalize(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return result.substring(0, Math.min(result.length(), 100));
    }
}
