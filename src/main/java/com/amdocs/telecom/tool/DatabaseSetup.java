package com.amdocs.telecom.tool;

import com.amdocs.telecom.TelecomApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

/** Explicit one-time installation into an empty public schema; not a startup component. */
public final class DatabaseSetup {

    private DatabaseSetup() { }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TelecomApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Collections.singletonMap("spring.sql.init.mode", "never"));
        try (ConfigurableApplicationContext context = application.run(args)) {
            boolean seed = context.getEnvironment().getProperty("telecom.setup.seed", Boolean.class, false);
            install(context.getBean(DataSource.class), seed);
            System.out.println("Database setup: SUCCESS (baseline installed" + (seed ? " with development fixtures)" : ")"));
        } catch (Exception exception) {
            // Never print the exception or datasource properties.
            System.err.println("Database setup: FAILURE (requires an accessible database with an empty public schema; no changes committed on installation failure)");
            System.exit(1);
        }
    }

    static void install(DataSource dataSource, boolean seed) throws Exception {
        byte[] baseline = Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"));
        String seedSql = seed ? readSql("db/seed-dev.sql") : null;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                // Serializes attempts made through this setup command.
                statement.execute("SELECT pg_advisory_xact_lock(74415001)");
                try (ResultSet rows = statement.executeQuery(
                        "SELECT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                        + "WHERE n.nspname='public') OR EXISTS (SELECT 1 FROM pg_type t "
                        + "JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public')")) {
                    rows.next();
                    if (rows.getBoolean(1)) {
                        throw new IllegalStateException("Public schema is not empty");
                    }
                }
                statement.execute("SET LOCAL search_path TO public");
                statement.execute(withoutTransactionBoundary(new String(baseline, StandardCharsets.UTF_8)));
                if (seed) { statement.execute(seedSql); }
                statement.execute("CREATE TABLE schema_migration (version INTEGER PRIMARY KEY, "
                        + "description VARCHAR(150) NOT NULL, checksum VARCHAR(64) NOT NULL, "
                        + "seeded BOOLEAN NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
                try (PreparedStatement record = connection.prepareStatement(
                        "INSERT INTO schema_migration (version, description, checksum, seeded) VALUES (1, ?, ?, ?)")) {
                    record.setString(1, "initial six-entity schema");
                    record.setString(2, checksum(baseline));
                    record.setBoolean(3, seed);
                    record.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static String readSql(String path) throws Exception {
        return withoutTransactionBoundary(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
    }

    static String withoutTransactionBoundary(String sql) {
        // Transaction ownership stays with this connection, including the seed and ledger.
        return sql.replaceAll("(?m)^(BEGIN|COMMIT);[\\t ]*\\r?$", "");
    }

    static String checksum(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
