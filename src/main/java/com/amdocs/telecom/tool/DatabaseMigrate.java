package com.amdocs.telecom.tool;

import com.amdocs.telecom.TelecomApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Explicit additive migration; never a normal startup bean. */
public final class DatabaseMigrate {
    private DatabaseMigrate() { }
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TelecomApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = app.run(args)) {
            boolean applied = upgrade(context.getBean(DataSource.class));
            System.out.println(applied ? "Database migration: SUCCESS (pending migrations applied through V008)" : "Database migration: SUCCESS (already current; checksums verified)");
            if (context.getEnvironment().getProperty("telecom.migrate.dev-accounts", Boolean.class, false)) {
                context.getBean(DevAccountProvisioner.class).provision();
                System.out.println("Development accounts created. Initial passwords are in ignored config/dev-accounts.properties.");
            }
        } catch (Exception exception) {
            System.err.println("Database migration/setup: FAILURE (check database access, baseline/checksums, and existing demo account setup; connection details suppressed)");
            System.exit(1);
        }
    }

    static boolean upgrade(DataSource dataSource) throws Exception {
        byte[] v1 = Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"));
        byte[] v2 = Files.readAllBytes(Paths.get("db/migrations/V002__authentication.sql"));
        byte[] v3 = Files.readAllBytes(Paths.get("db/migrations/V003__ticket_intake.sql"));
        byte[] v4 = Files.readAllBytes(Paths.get("db/migrations/V004__engineer_assignment.sql"));
        byte[] v5 = Files.readAllBytes(Paths.get("db/migrations/V005__manual_lifecycle.sql"));
        byte[] v6 = Files.readAllBytes(Paths.get("db/migrations/V006__sla_escalation.sql"));
        byte[] v7 = Files.readAllBytes(Paths.get("db/migrations/V007__notifications_network_events.sql"));
        byte[] v8 = Files.readAllBytes(Paths.get("db/migrations/V008__case_study_resolution_codes.sql"));
        byte[][] migrations = {v1, v2, v3, v4, v5, v6, v7, v8};
        String[] descriptions = {"initial six-entity schema", "authentication and profile links", "ticket intake notifications and audit", "engineer assignment rules and audit details", "manual lifecycle resolution cancellation and feedback", "SLA monitoring and escalation", "notification processing and network events", "case study resolution code catalog"};
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(74415001)");
                statement.execute("SET LOCAL search_path TO public");
                Map<Integer, String> applied = new HashMap<>();
                try (ResultSet rows = statement.executeQuery("SELECT version, checksum FROM schema_migration ORDER BY version")) {
                    while (rows.next()) { applied.put(rows.getInt(1), rows.getString(2)); }
                }
                if (!DatabaseSetup.checksum(v1).equals(applied.get(1)) || applied.keySet().stream().anyMatch(v -> v < 1 || v > migrations.length)) {
                    throw new IllegalStateException("Baseline missing, changed or newer than this runner");
                }
                boolean changed = false;
                // Verify the entire existing contiguous ledger before any DDL.
                for (int version = 1; version <= applied.size(); version++) {
                    if (!DatabaseSetup.checksum(migrations[version - 1]).equals(applied.get(version))) {
                        throw new IllegalStateException("Migration missing or checksum changed");
                    }
                }
                for (int version = applied.size() + 1; version <= migrations.length; version++) {
                    statement.execute(DatabaseSetup.withoutTransactionBoundary(new String(migrations[version - 1], StandardCharsets.UTF_8)));
                    try (PreparedStatement record = connection.prepareStatement(
                            "INSERT INTO schema_migration (version,description,checksum,seeded) VALUES (?,?,?,FALSE)")) {
                        record.setInt(1, version);
                        record.setString(2, descriptions[version - 1]);
                        record.setString(3, DatabaseSetup.checksum(migrations[version - 1]));
                        record.executeUpdate();
                    }
                    changed = true;
                }
                connection.commit();
                return changed;
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }
}
