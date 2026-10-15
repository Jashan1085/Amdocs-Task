package com.amdocs.telecom.tool;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseMigrateTest {
    private final DataSource source = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final ResultSet ledger = mock(ResultSet.class);
    private void configure(String checksum) throws Exception {
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(ledger);
        when(ledger.next()).thenReturn(true, false);
        when(ledger.getInt(1)).thenReturn(1);
        when(ledger.getString(2)).thenReturn(checksum);
    }
    @Test void v8FailureRollsBackWithoutChangingExistingCatalogOrLedger() throws Exception {
        configure("unused");
        String[] names={"V001__initial_schema.sql","V002__authentication.sql","V003__ticket_intake.sql","V004__engineer_assignment.sql","V005__manual_lifecycle.sql","V006__sla_escalation.sql","V007__notifications_network_events.sql"};
        when(ledger.next()).thenReturn(true,true,true,true,true,true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2,3,4,5,6,7);
        java.util.List<String> hashes=new java.util.ArrayList<>();
        for(String name:names) { hashes.add(DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations",name)))); }
        when(ledger.getString(2)).thenReturn(hashes.get(0),hashes.get(1),hashes.get(2),hashes.get(3),hashes.get(4),hashes.get(5),hashes.get(6));
        when(statement.execute(contains("ALTER TABLE ticket_resolution DROP CONSTRAINT"))).thenThrow(new SQLException("test V008 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback(); verify(connection,never()).commit();
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void v7FailureRollsBackAndDoesNotRecordVersion() throws Exception {
        configure("unused");
        String[] names={"V001__initial_schema.sql","V002__authentication.sql","V003__ticket_intake.sql","V004__engineer_assignment.sql","V005__manual_lifecycle.sql","V006__sla_escalation.sql"};
        when(ledger.next()).thenReturn(true,true,true,true,true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2,3,4,5,6);
        java.util.List<String> hashes=new java.util.ArrayList<>();
        for(String name:names) { hashes.add(DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations",name)))); }
        when(ledger.getString(2)).thenReturn(hashes.get(0),hashes.get(1),hashes.get(2),hashes.get(3),hashes.get(4),hashes.get(5));
        when(statement.execute(contains("CREATE TABLE notification_receipt"))).thenThrow(new SQLException("test V007 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback(); verify(connection,never()).commit();
        verify(statement,never()).execute(contains("CREATE TABLE escalation_recipient"));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void refusesAnAlteredBaselineBeforeRunningDdl() throws Exception {
        configure("wrong-checksum");
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(IllegalStateException.class);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(statement, never()).execute(contains("CREATE TABLE app_user"));
    }
    @Test void migrationFailureRollsBackWithoutRecordingVersion() throws Exception {
        configure(DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))));
        when(statement.execute(contains("CREATE TABLE app_user"))).thenThrow(new SQLException("test DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).prepareStatement(anyString());
    }
    @Test void v3FailureRollsBackAndLeavesExistingVersionsAlone() throws Exception {
        configure(DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))));
        when(ledger.next()).thenReturn(true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2);
        when(ledger.getString(2)).thenReturn(
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V002__authentication.sql"))));
        when(statement.execute(contains("CREATE SEQUENCE ticket_number_seq"))).thenThrow(new SQLException("test V003 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback();
        verify(connection,never()).commit();
        verify(statement,never()).execute(contains("CREATE TABLE app_user"));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void v4FailureRollsBackWithoutChangingExistingMigrations() throws Exception {
        configure("unused");
        when(ledger.next()).thenReturn(true,true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2,3);
        when(ledger.getString(2)).thenReturn(
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V002__authentication.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V003__ticket_intake.sql"))));
        when(statement.execute(contains("CREATE TABLE category_specialization"))).thenThrow(new SQLException("test V004 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback(); verify(connection,never()).commit();
        verify(statement,never()).execute(contains("CREATE TABLE app_user"));
        verify(statement,never()).execute(contains("CREATE SEQUENCE ticket_number_seq"));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void v6FailureRollsBackWithoutRecordingOrReplayingEarlierVersions() throws Exception {
        configure("unused");
        when(ledger.next()).thenReturn(true,true,true,true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2,3,4,5);
        when(ledger.getString(2)).thenReturn(
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V002__authentication.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V003__ticket_intake.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V004__engineer_assignment.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V005__manual_lifecycle.sql"))));
        when(statement.execute(contains("CREATE TABLE escalation_recipient"))).thenThrow(new SQLException("test V006 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback(); verify(connection,never()).commit();
        verify(statement,never()).execute(contains("CREATE TABLE ticket_resolution"));
        verify(connection,never()).prepareStatement(anyString());
    }
    @Test void v5FailureLeavesEarlierVersionsUnchanged() throws Exception {
        configure("unused");
        when(ledger.next()).thenReturn(true,true,true,true,false);
        when(ledger.getInt(1)).thenReturn(1,2,3,4);
        when(ledger.getString(2)).thenReturn(
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V001__initial_schema.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V002__authentication.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V003__ticket_intake.sql"))),
                DatabaseSetup.checksum(Files.readAllBytes(Paths.get("db/migrations/V004__engineer_assignment.sql"))));
        when(statement.execute(contains("CREATE TABLE ticket_resolution"))).thenThrow(new SQLException("test V005 DDL failure"));
        assertThatThrownBy(() -> DatabaseMigrate.upgrade(source)).isInstanceOf(SQLException.class);
        verify(connection).rollback(); verify(connection,never()).commit();
        verify(connection,never()).prepareStatement(anyString());
    }
}
