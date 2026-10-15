package com.amdocs.telecom.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in, read-only inspection of the configured PostgreSQL schema. */
@EnabledIfSystemProperty(named = "telecom.integration", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.connection-timeout=5000",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.read-only=true",
        "logging.level.root=OFF"
})
class DatabaseSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void configuredDatabaseContainsTheApprovedBaseline() {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
        System.out.println("Database baseline inspection: " + tables);
        assertThat(tables).contains("customer", "telecom_service", "network_engineer",
                "sla_configuration", "trouble_ticket", "ticket_status_history");

        Integer enabledGuards = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_trigger "
                        + "WHERE tgrelid IN ('public.ticket_status_history'::regclass, "
                        + "'public.sla_configuration'::regclass) "
                        + "AND tgname IN ('protect_ticket_status_history', 'protect_sla_configuration') "
                        + "AND tgenabled IN ('O', 'A') AND NOT tgisinternal",
                Integer.class);
        assertThat(enabledGuards).as("history and SLA mutation guards").isEqualTo(2);
    }

    @Test
    void migrationLedgerMatchesTheInstalledBaseline() throws Exception {
        StringBuilder checksum = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(
                Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql")))) {
            checksum.append(String.format("%02x", value & 0xff));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT checksum FROM schema_migration WHERE version = 1", String.class))
                .isEqualTo(checksum.toString());
    }

    @Test
    void installedDevelopmentFixturesHaveConsistentHistoryAndDeadlines() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT seeded FROM schema_migration WHERE version = 1", Boolean.class)));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trouble_ticket "
                + "WHERE ticket_number BETWEEN 'TT-2026-000001' AND 'TT-2026-000008'", Integer.class))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trouble_ticket t JOIN sla_configuration p "
                        + "ON (p.priority,p.policy_version)=(t.priority,t.sla_policy_version) "
                        + "WHERE t.response_deadline <> t.created_at + p.response_minutes * INTERVAL '1 minute' "
                        + "OR t.resolution_deadline <> t.created_at + p.resolution_minutes * INTERVAL '1 minute'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT old_status, LAG(new_status) OVER "
                        + "(PARTITION BY ticket_id ORDER BY changed_at,history_id) AS previous_status "
                        + "FROM ticket_status_history) history "
                        + "WHERE old_status IS DISTINCT FROM previous_status", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trouble_ticket t WHERE t.status IS DISTINCT FROM "
                        + "(SELECT h.new_status FROM ticket_status_history h WHERE h.ticket_id=t.ticket_id "
                        + "ORDER BY h.changed_at DESC,h.history_id DESC LIMIT 1)", Integer.class)).isZero();
    }

    @Test
    void currentMigrationsAreInstalledAndChecksumsAreCurrent() throws Exception {
        String[] files={"V001__initial_schema.sql","V002__authentication.sql","V003__ticket_intake.sql","V004__engineer_assignment.sql","V005__manual_lifecycle.sql","V006__sla_escalation.sql","V007__notifications_network_events.sql","V008__case_study_resolution_codes.sql"};
        for(int i=0;i<files.length;i++) {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get("db/migrations",files[i])));
            String expected=String.format("%064x",new java.math.BigInteger(1,digest));
            assertThat(jdbcTemplate.queryForObject("SELECT checksum FROM schema_migration WHERE version=?",String.class,i+1))
                    .isEqualTo(expected);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_trigger WHERE tgname='protect_audit_event' "
                + "AND tgrelid='audit_event'::regclass AND tgenabled IN ('O','A') AND NOT tgisinternal",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('public.ticket_number_seq') IS NOT NULL",Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category_specialization",Integer.class))
                .isEqualTo(com.amdocs.telecom.model.IncidentCategory.values().length);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE num_nonnulls(customer_id,engineer_id,escalation_recipient_level)<>1",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM escalation_recipient",Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_trigger WHERE tgname='protect_escalation_history' AND tgrelid='escalation_history'::regclass AND tgenabled IN ('O','A') AND NOT tgisinternal",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event WHERE (actor_kind='USER' AND actor_user_id IS NULL) OR (actor_kind='SYSTEM' AND actor_user_id IS NOT NULL) OR actor_kind NOT IN ('USER','SYSTEM')",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_escalation_state s WHERE s.highest_level<>COALESCE((SELECT MAX(h.escalation_level) FROM escalation_history h WHERE h.ticket_id=s.ticket_id),0)",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_trigger WHERE tgname IN ('protect_ticket_resolution','protect_ticket_cancellation','protect_ticket_feedback') AND tgenabled IN ('O','A') AND NOT tgisinternal",Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_resolution r JOIN trouble_ticket t USING(ticket_id) WHERE t.status NOT IN ('RESOLVED','CLOSED') OR r.resolved_at IS DISTINCT FROM t.resolved_at",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_feedback f JOIN trouble_ticket t USING(ticket_id) JOIN telecom_service s USING(service_id) WHERE t.status<>'CLOSED' OR f.customer_id<>s.customer_id",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_cancellation c JOIN trouble_ticket t USING(ticket_id) WHERE t.status<>'CANCELLED'",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_trigger WHERE tgname IN ('protect_notification_receipt','protect_event_ticket') AND tgenabled IN ('O','A') AND NOT tgisinternal",Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_receipt r JOIN notification n USING(notification_id) WHERE n.delivery_status<>'DELIVERED'",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_ticket e JOIN trouble_ticket t USING(ticket_id) WHERE e.service_id<>t.service_id",Integer.class)).isZero();
        String resolutionCodes=jdbcTemplate.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='ticket_resolution'::regclass AND conname='ticket_resolution_resolution_code_check'",String.class);
        for(com.amdocs.telecom.model.ResolutionCode code:com.amdocs.telecom.model.ResolutionCode.values()) { assertThat(resolutionCodes).contains("'"+code.name()+"'"); }
        System.out.println("Read-only ticket state: tickets="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trouble_ticket",Long.class)
                +", history="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_status_history",Long.class)
                +", notifications="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification",Long.class)
                +", audit="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class)
                +", receipts="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_receipt",Long.class)
                +", events="+jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_event",Long.class));
    }
}
