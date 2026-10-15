package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.security.*;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAuthenticationDAO {
    private final JdbcTemplate jdbc;
    private static final String USER = "SELECT u.*, c.customer_id, e.engineer_id FROM app_user u "
            + "LEFT JOIN customer c ON c.user_id=u.user_id LEFT JOIN network_engineer e ON e.user_id=u.user_id ";

    public JdbcAuthenticationDAO(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<AuthAccount> findByUsername(String username, boolean lock) {
        return account(USER + "WHERE u.username=?" + (lock ? " FOR UPDATE OF u" : ""), username);
    }
    public Optional<AuthAccount> findById(long userId, boolean lock) {
        return account(USER + "WHERE u.user_id=?" + (lock ? " FOR UPDATE OF u" : ""), userId);
    }
    private Optional<AuthAccount> account(String sql, Object value) {
        AuthAccount account = DataAccessUtils.singleResult(jdbc.query(sql, (rs, row) -> new AuthAccount(
                rs.getLong("user_id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getInt("failed_attempts"),
                instant(rs, "locked_until"), rs.getLong("auth_version"), nullableLong(rs, "customer_id"),
                nullableLong(rs, "engineer_id"), Collections.emptyList()), value));
        if (account == null) { return Optional.empty(); }
        List<Role> roles = jdbc.query("SELECT role_code FROM user_role WHERE user_id=? ORDER BY role_code",
                (rs, row) -> Role.valueOf(rs.getString(1)), account.getUserId());
        return Optional.of(new AuthAccount(account.getUserId(), account.getUsername(), account.getDisplayName(),
                account.getPasswordHash(), account.isEnabled(), account.getFailedAttempts(), account.getLockedUntil(),
                account.getAuthVersion(), account.getCustomerId(), account.getEngineerId(), roles));
    }
    public void updateFailures(long id, int attempts, Instant lockedUntil, Instant now) {
        jdbc.update("UPDATE app_user SET failed_attempts=?, locked_until=?, updated_at=? WHERE user_id=?",
                attempts, timestamp(lockedUntil), timestamp(now), id);
    }
    public void changePassword(long id, String hash, Instant now) {
        jdbc.update("UPDATE app_user SET password_hash=?, auth_version=auth_version+1, failed_attempts=0, "
                + "locked_until=NULL, updated_at=? WHERE user_id=?", hash, timestamp(now), id);
    }
    public void record(Long userId, String username, String outcome, Instant now) {
        String safeUsername = username == null ? "" : username.substring(0, Math.min(100, username.length()));
        jdbc.update("INSERT INTO login_history (user_id, attempted_username, outcome, occurred_at) VALUES (?,?,?,?)",
                userId, safeUsername, outcome, timestamp(now));
    }
    public void invalidateChallenges(long userId, OtpPurpose purpose, Instant now) {
        jdbc.update("UPDATE otp_challenge SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL"
                        + (purpose == null ? "" : " AND purpose=?"),
                purpose == null ? new Object[]{timestamp(now), userId}
                        : new Object[]{timestamp(now), userId, purpose.name()});
    }
    public void insertChallenge(UUID id, AuthAccount user, OtpPurpose purpose, String bindingHash,
                                String codeHash, Instant now, Instant expiry) {
        jdbc.update("INSERT INTO otp_challenge (challenge_id,user_id,purpose,binding_hash,code_hash,auth_version,"
                        + "created_at,expires_at) VALUES (?,?,?,?,?,?,?,?)", id, user.getUserId(), purpose.name(),
                bindingHash, codeHash, user.getAuthVersion(), timestamp(now), timestamp(expiry));
    }
    public Optional<OtpRecord> findChallenge(UUID id, boolean lock) {
        return Optional.ofNullable(DataAccessUtils.singleResult(jdbc.query(
                "SELECT * FROM otp_challenge WHERE challenge_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new OtpRecord(rs.getObject("challenge_id", UUID.class), rs.getLong("user_id"),
                        OtpPurpose.valueOf(rs.getString("purpose")), rs.getString("binding_hash"), rs.getString("code_hash"),
                        rs.getLong("auth_version"), rs.getInt("attempts"), instant(rs, "created_at"),
                        instant(rs, "expires_at"), instant(rs, "consumed_at")), id)));
    }
    public void failChallenge(UUID id, Instant now) {
        jdbc.update("UPDATE otp_challenge SET attempts=attempts+1, "
                        + "consumed_at=CASE WHEN attempts+1>=5 THEN ? ELSE consumed_at END WHERE challenge_id=?",
                timestamp(now), id);
    }
    public void consumeChallenge(UUID id, Instant now) {
        jdbc.update("UPDATE otp_challenge SET consumed_at=? WHERE challenge_id=?", timestamp(now), id);
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
